package org.obiba.opal.r.datasource;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.obiba.core.util.FileUtil;
import org.obiba.magma.*;
import org.obiba.magma.datasource.csv.CsvDatasource;
import org.obiba.magma.support.Initialisables;
import org.obiba.magma.type.*;
import org.obiba.opal.r.magma.util.DoubleRange;
import org.obiba.opal.r.magma.util.IntegerRange;
import org.obiba.opal.r.magma.util.NumberRange;
import org.obiba.opal.spi.r.AbstractROperation;
import org.obiba.opal.spi.r.ROperation;
import org.obiba.opal.spi.r.datasource.RSessionHandler;
import org.obiba.opal.spi.r.datasource.magma.MagmaRRuntimeException;
import org.rosuda.REngine.Rserve.RConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A datasource that writes table data into a tibble in a R session. It first writes data in a CSV file and dictionary
 * information in R files. All these files are then sent to the R workspace and read into a tibble using the readr package.
 */
public class RAssignDatasource extends CsvDatasource {

  private static final Logger log = LoggerFactory.getLogger(RAssignDatasource.class);

  private static final String DATA_FILE_NAME = ".data.csv.zip";

  private static final String COLS_FILE_NAME = ".variable-cols.R";

  private static final String ATTR_FILE_NAME = ".variable-attributes.R";

  private Path workDir;

  private final String symbol;

  private final RConnection rConnection;

  private final RSessionHandler rSessionHandler;

  private boolean withMissings = true;

  public RAssignDatasource(String name, String symbol, RConnection rConnection) {
    super(name);
    this.symbol = symbol;
    this.rConnection = rConnection;
    this.rSessionHandler = null;
  }

  public RAssignDatasource(String name, String symbol, RSessionHandler rSessionHandler) {
    super(name);
    this.symbol = symbol;
    this.rConnection = null;
    this.rSessionHandler = rSessionHandler;
  }

  @Override
  protected void onInitialise() {
    try {
      workDir = Files.createTempDirectory(getName() + "-");
    } catch (IOException e) {
      throw new MagmaRRuntimeException("Failed at creating R assign work directory", e);
    }
  }

  @Override
  protected void onDispose() {
    if (workDir != null) {
      try {
        FileUtil.delete(workDir.toFile());
      } catch (IOException e) {
        log.warn("Failed at removing work directory {}", workDir, e);
      }
    }
    if (rSessionHandler != null) {
      rSessionHandler.onDispose();
    }
  }

  @NotNull
  @Override
  public ValueTableWriter createWriter(@NotNull String tableName, @NotNull String entityType) {
    File parentFolder = new File(workDir.toFile(), tableName);
    parentFolder.mkdirs();
    if (!hasValueTable(tableName)) {
      addValueTable(tableName, new File(parentFolder, DATA_FILE_NAME), entityType);
      Initialisables.initialise(getValueTable(tableName));
    }
    return new RAssignValueTableWriter(super.createWriter(tableName, entityType), tableName, entityType, parentFolder);
  }

  protected String getSymbol(String tableName) {
    return symbol;
  }

  void setWithMissings(boolean withMissings) {
    this.withMissings = withMissings;
  }

  //
  // Private methods
  //

  private boolean hasRConnection() {
    return rConnection != null;
  }

  private RConnection getRConnection() {
    return rConnection;
  }

  private boolean hasRSessionHandler() {
    return rSessionHandler != null;
  }

  private RSessionHandler getRSessionHandler() {
    return rSessionHandler;
  }

  private boolean isWithMissings() {
    return withMissings;
  }

  private void execute(ROperation rOperation) {
    if (hasRConnection())
      rOperation.doWithConnection(getRConnection());
    else if (hasRSessionHandler())
      getRSessionHandler().getSession().execute(rOperation);
    else
      throw new IllegalStateException("A R connection or a R session is required");
  }

  //
  // Inner classes
  //

  /**
   * A table writer wrapper to write data in CSV file and variables in R file, to be read by readr R package.
   */
  private class RAssignValueTableWriter extends AbstractROperation implements ValueTableWriter {

    private final ValueTableWriter wrapped;

    private final String tableName;

    private final String entityType;

    private File dataFile;

    private File colsFile;

    private File attributesFile;

    RAssignValueTableWriter(ValueTableWriter wrapped, String tableName, String entityType, File parentFolder) {
      this.wrapped = wrapped;
      this.tableName = tableName;
      this.entityType = entityType;
      this.dataFile = new File(parentFolder, DATA_FILE_NAME);
      this.colsFile = new File(parentFolder, COLS_FILE_NAME);
      this.attributesFile = new File(parentFolder, ATTR_FILE_NAME);
    }

    @Override
    public VariableWriter writeVariables() {
      return new RAssignVariableWriter(tableName, entityType, colsFile, attributesFile);
    }

    @NotNull
    @Override
    public ValueSetWriter writeValueSet(@NotNull VariableEntity entity) {
      if (withMissings)
        return wrapped.writeValueSet(entity);
      else
        return new RAssignValueSetWriter(wrapped.writeValueSet(entity));
    }

    @Override
    public void close() {
      wrapped.close();
      // do read data and spec files with readr
      execute(this);
    }

    @Override
    protected void doWithConnection() {
      if (dataFile.exists()) {
        doSendFiles();
        doReadCSVFile();
      }
    }

    private void doSendFiles() {
      // then send this file to R session's workspace
      log.debug("Writing files from {} to R session workspace", dataFile.getParentFile().getAbsolutePath());
      for (File child : dataFile.getParentFile().listFiles()) {
        writeFile(child.getName(), child);
      }
    }

    /**
     * Read the CSV file in the R workspace with the appropriate data types.
     */
    private void doReadCSVFile() {
      log.debug("Reading the CSV file into a tibble");
      ensurePackage("readr");
      eval(String.format("base::source('%s')", COLS_FILE_NAME));
      eval(String.format("base::is.null(base::assign('%s', readr::read_csv('%s', col_types = .cols)))", getSymbol(tableName), DATA_FILE_NAME));
      eval("base::rm(.cols)");
      eval(String.format("base::source('%s')", ATTR_FILE_NAME));
      eval(String.format("base::unlink('%s')", DATA_FILE_NAME));
      eval(String.format("base::unlink('%s')", COLS_FILE_NAME));
      eval(String.format("base::unlink('%s')", ATTR_FILE_NAME));
      log.debug("Symbol {} assigned", getSymbol(tableName));
    }

  }

  private class RAssignValueSetWriter implements ValueTableWriter.ValueSetWriter {

    private final ValueTableWriter.ValueSetWriter wrapped;

    private RAssignValueSetWriter(ValueTableWriter.ValueSetWriter wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public void writeValue(@NotNull Variable variable, Value value) {
      Value rval = value;
      if (!isWithMissings() && variable.isMissingValue(value)) {
        rval = variable.getValueType().nullValue();
      }
      wrapped.writeValue(variable, rval);
    }

    @Override
    public void remove() {
      wrapped.remove();
    }

    @Override
    public void close() {
      wrapped.close();
    }
  }

  /**
   * Write column type specification for each variable.
   */
  private class RAssignVariableWriter implements ValueTableWriter.VariableWriter {

    private final PrintWriter colsWriter;

    private final PrintWriter attributesWriter;

    private final String tableName;

    RAssignVariableWriter(String tableName, String entityType, File colsFile, File attributesFile) {
      this.tableName = tableName;
      try {
        this.colsWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(colsFile)));
      } catch (FileNotFoundException e) {
        throw new MagmaRRuntimeException("Not able to write columns specification file: " + colsFile, e);
      }
      colsWriter.println(".cols <- readr::cols(");
      colsWriter.print(String.format("  '%s' = col_character()", getEntityIdName(entityType)));
      try {
        this.attributesWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(attributesFile)));
      } catch (FileNotFoundException e) {
        throw new MagmaRRuntimeException("Not able to write columns attributes file: " + attributesFile, e);
      }
    }

    @Override
    public void writeVariable(@NotNull Variable variable) {
      writeVariableColumnSpecification(variable);
      writeVariableAttributes(variable);
    }

    private void writeVariableColumnSpecification(Variable variable) {
      // column type
      String type;
      if (variable.getValueType().equals(IntegerType.get()))
        type = "integer";
      else if (variable.getValueType().equals(DecimalType.get()))
        type = "double";
      else if (variable.getValueType().equals(BooleanType.get()))
        type = "logical";
      else if (variable.getValueType().equals(DateType.get()))
        type = "date";
      else if (variable.getValueType().equals(DateTimeType.get()))
        type = "datetime";
      else
        type = "character";
      colsWriter.println(",");
      colsWriter.print(String.format("  '%s' = col_%s()", variable.getName(), type));
    }

    private void writeVariableAttributes(Variable variable) {
      // attributes
      List<String> attributesList = Lists.newArrayList();
      for (Map.Entry<String, String> entry : asAttributesMap(variable).entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if (!name.equals("class")) { // exclude class attribute as it is an interpreted by R
          attributesList.add(String.format("'%s' = '%s'", name, normalize(value)));
        }
        // to help haven R package to write spss or stata formats
        if (name.equals("spss::format")) {
          attributesList.add(String.format("'format.spss' = '%s'", value));
        } else if (name.equals("stata::format")) {
          attributesList.add(String.format("'format.stata' = '%s'", value));
        }
      }
      // attributes from categories
      if (variable.hasCategories()) {
        List<Category> missingCats = Lists.newArrayList();
        for (Category cat : variable.getCategories()) {
          if (cat.isMissing()) {
            missingCats.add(cat);
          }
        }
        if (!missingCats.isEmpty()) {
          attributesList.add(String.format("class = c('%s')",
              Joiner.on("', '").join("haven_labelled_spss", "haven_labelled")));
          NumberRange naRange = getCategoriesMissingNumberRange(variable, missingCats.stream().map(Category::getName).collect(Collectors.toList()));
          if (naRange != null && naRange.hasRange()) {
            attributesList.add(String.format("na_range = c(%s)",
                Joiner.on(", ").join(naRange.getRangeMin().toString(), naRange.getRangeMax().toString())));
          }
          // add discrete missing values after na_range as the missingCats may have been modified
          if (!missingCats.isEmpty()) {
            if (naRange != null && missingCats.size() > 1) {
              log.warn("Variable {}: SPSS format does not support more than one discrete missing value in addition to a missing values range.", variable.getName());
            }
            attributesList.add(String.format("na_values = c(%s)",
                Joiner.on(", ").join(getLabelledCategories(variable, missingCats))));
          }
        } else {
          attributesList.add("class = 'haven_labelled'");
        }
        attributesList.add(String.format("labels = c(%s)",
            Joiner.on(", ").join(getLabelledCategories(variable, variable.getCategories()))));
      }
      if (!attributesList.isEmpty())
        attributesWriter.println(String.format("base::attributes(`%s`[['%s']]) <- list(%s)",
            getSymbol(tableName), variable.getName(), Joiner.on(", ").join(attributesList)));
    }

    private List<String> getLabelledCategories(Variable variable, Collection<Category> categories) {
      List<String> labelledCat = Lists.newArrayList();
      for (Category cat : categories) {
        String label = normalize(getLabel(cat));
        String value;
        if (variable.getValueType().isNumeric())
          value = cat.getName();
        else if (BooleanType.get().equals(variable.getValueType()))
          value = cat.getName().toUpperCase();
        else
          value = "'" + normalize(cat.getName()) + "'";
        labelledCat.add(String.format("'%s'=%s", label, value));
      }
      return labelledCat;
    }

    @Override
    public void removeVariable(@NotNull Variable variable) {
      // ignore
    }

    @Override
    public void close() {
      // finish cols
      colsWriter.println();
      colsWriter.println(")");
      colsWriter.flush();
      colsWriter.close();
      // finish attributes
      attributesWriter.flush();
      attributesWriter.close();
    }

    private String normalize(String label) {
      return label.replaceAll("'", "\\\\'");
    }

    /**
     * Extract the category label.
     *
     * @param category
     * @return The label or the name if label is not found.
     */
    private String getLabel(Category category) {
      Map<String, String> attributesMap = asAttributesMap(category);
      return attributesMap.containsKey("label") ? attributesMap.get("label") : category.getName();
    }

    /**
     * Merge the {@link Attribute} namespace and name as the map key, and the locale and value as the map value.
     *
     * @param attributeAware
     * @return
     */
    private Map<String, String> asAttributesMap(AttributeAware attributeAware) {
      // per namespace::name, per locale
      Map<String, Map<String, String>> attributesMap = Maps.newHashMap();
      if (attributeAware.hasAttributes()) {
        for (Attribute attr : attributeAware.getAttributes()) {
          if (Strings.isNullOrEmpty(attr.getValue().toString())) continue;
          String name = attr.getName();
          if (attr.hasNamespace()) name = attr.getNamespace() + "::" + name;
          if (!attributesMap.containsKey(name)) {
            attributesMap.put(name, Maps.newHashMap());
          }
          attributesMap.get(name).put(attr.isLocalised() ? attr.getLocale().toLanguageTag() : "", attr.getValue().toString());
        }
      }
      // per namespace::name
      Map<String, String> rval = Maps.newHashMap();
      attributesMap.forEach((name, localeMap) -> {
        String content = localeMap.entrySet()
            .stream()
            .map(entry -> (Strings.isNullOrEmpty(entry.getKey()) ? "" : "(" + entry.getKey() + ") ") + entry.getValue())
            .collect(Collectors.joining(" | "));
        rval.put(name, content);
      });
      return rval;
    }


    private NumberRange getCategoriesMissingNumberRange(Variable variable, List<String> missingCats) {
      if (!variable.getValueType().isNumeric()) return null;
      if (missingCats.size() <= 3) return null; // spss allows a max of 3 discrete missings

      if (IntegerType.get().equals(variable.getValueType()))
        return new IntegerRange(variable.getCategories().stream().map(Category::getName).collect(Collectors.toList()), missingCats);

      return new DoubleRange(variable.getCategories().stream().map(Category::getName).collect(Collectors.toList()), missingCats);
    }

  }

}
