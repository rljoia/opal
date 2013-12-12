/*
 * Copyright (c) 2013 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.opal.web.system.identifiers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.obiba.magma.Datasource;
import org.obiba.magma.DatasourceFactory;
import org.obiba.magma.MagmaEngine;
import org.obiba.magma.MagmaRuntimeException;
import org.obiba.magma.NoSuchDatasourceException;
import org.obiba.magma.NoSuchValueTableException;
import org.obiba.magma.ValueTable;
import org.obiba.magma.ValueTableWriter;
import org.obiba.magma.Variable;
import org.obiba.magma.lang.Closeables;
import org.obiba.magma.support.Disposables;
import org.obiba.opal.core.domain.participant.identifier.impl.DefaultParticipantIdentifierImpl;
import org.obiba.opal.core.identifiers.IdentifiersMapping;
import org.obiba.opal.core.runtime.OpalRuntime;
import org.obiba.opal.core.service.IdentifiersImportService;
import org.obiba.opal.core.service.IdentifiersTableService;
import org.obiba.opal.core.identifiers.IdentifiersMaps;
import org.obiba.opal.web.magma.ClientErrorDtos;
import org.obiba.opal.web.magma.Dtos;
import org.obiba.opal.web.magma.support.DatasourceFactoryRegistry;
import org.obiba.opal.web.model.Magma;
import org.obiba.opal.web.ws.security.AuthenticatedByCookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import au.com.bytecode.opencsv.CSVWriter;

@Component
@Transactional
@Scope("request")
@Path("/system/identifiers/mapping/{name}")
@Api(value = "/system/identifiers/mapping/{name}",
    description = "Operations about a specific identifiers mapping")
public class IdentifiersMappingResource extends AbstractIdentifiersResource {

  @Autowired
  private OpalRuntime opalRuntime;

  @Autowired
  private IdentifiersTableService identifiersTableService;

  @Autowired
  private IdentifiersImportService identifiersImportService;

  @Autowired
  private DatasourceFactoryRegistry datasourceFactoryRegistry;

  @PathParam("name")
  private String name;

  @Override
  protected IdentifiersTableService getIdentifiersTableService() {
    return identifiersTableService;
  }

  @Override
  protected OpalRuntime getOpalRuntime() {
    return opalRuntime;
  }

  @GET
  @ApiOperation(value = "Get a specific identifiers mapping for an entity type")
  public Magma.VariableDto get(@QueryParam("type") @DefaultValue("Participant") String entityType) {
    ValueTable table = getValueTable(entityType);
    Variable variable = table.getVariable(name);

    return Dtos.asDto(variable).build();
  }

  @DELETE
  @ApiOperation(value = "Delete a specific identifiers mapping for an entity type")
  public Response delete(@QueryParam("type") @DefaultValue("Participant") String entityType) {
    ValueTableWriter vtw = null;
    ValueTableWriter.VariableWriter vw = null;
    try {
      ValueTable table = getValueTable(entityType);
      // The variable must exist
      Variable v = table.getVariable(name);
      vtw = table.getDatasource().createWriter(table.getName(), table.getEntityType());

      vw = vtw.writeVariables();
      vw.removeVariable(v);

      return Response.ok().build();

    } finally {
      Closeables.closeQuietly(vw);
      Closeables.closeQuietly(vtw);
    }
  }

  @GET
  @Path("/entities")
  @ApiOperation(value = "Get identifiers as entities")
  public List<Magma.VariableEntityDto> getUnitEntities(
      @QueryParam("type") @DefaultValue("Participant") String entityType) {
    return Lists.newArrayList(Iterables
        .transform(new IdentifiersMaps(getValueTable(entityType), name).getPrivateEntities(),
            Dtos.variableEntityAsDtoFunc));
  }

  @GET
  @Path("/_count")
  public String getEntitiesCount(@QueryParam("type") @DefaultValue("Participant") String entityType) {
    return String.valueOf(Iterables.size(getUnitIdentifiers(entityType)));
  }

  @POST
  @Path("/_import")
  public Response importIdentifiers(Magma.DatasourceFactoryDto datasourceFactoryDto,
      @QueryParam("type") @DefaultValue("Participant") String entityType, @QueryParam("select") String select) {
    Response response = null;

    Datasource sourceDatasource = createTransientDatasource(datasourceFactoryDto);

    try {
      identifiersImportService
          .importIdentifiers(new IdentifiersMapping(name, entityType), sourceDatasource, select);
      response = Response.ok().build();
    } catch(NoSuchDatasourceException ex) {
      response = Response.status(Response.Status.NOT_FOUND)
          .entity(ClientErrorDtos.getErrorMessage(Response.Status.NOT_FOUND, "DatasourceNotFound", ex)).build();
    } catch(NoSuchValueTableException ex) {
      response = Response.status(Response.Status.NOT_FOUND)
          .entity(ClientErrorDtos.getErrorMessage(Response.Status.NOT_FOUND, "ValueTableNotFound", ex)).build();
    } catch(IOException ex) {
      response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
          ClientErrorDtos.getErrorMessage(Response.Status.INTERNAL_SERVER_ERROR, "DatasourceCopierIOException", ex))
          .build();
    } finally {
      Disposables.silentlyDispose(sourceDatasource);
    }

    return response;
  }

  /**
   * Generate identifiers.
   * @param entityType
   * @param size
   * @param zeros
   * @param prefix
   * @return
   */
  @POST
  @Path("/_generate")
  public Response importIdentifiers(@QueryParam("type") @DefaultValue("Participant") String entityType,
      @QueryParam("size") Integer size, @QueryParam("zeros") Boolean zeros, @QueryParam("prefix") String prefix) {
    try {
      DefaultParticipantIdentifierImpl pId = new DefaultParticipantIdentifierImpl();
      if(size != null) pId.setKeySize(size);
      if(zeros != null) pId.setAllowStartWithZero(zeros);
      if(prefix != null) pId.setPrefix(prefix);
      int count = identifiersImportService.importIdentifiers(new IdentifiersMapping(name, entityType), pId);
      return Response.ok().entity(Integer.toString(count)).build();
    } catch(NoSuchDatasourceException ex) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ClientErrorDtos.getErrorMessage(Response.Status.NOT_FOUND, "DatasourceNotFound", ex)).build();
    } catch(NoSuchValueTableException ex) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(ClientErrorDtos.getErrorMessage(Response.Status.NOT_FOUND, "ValueTableNotFound", ex)).build();
    } catch(MagmaRuntimeException ex) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ClientErrorDtos.getErrorMessage(Response.Status.INTERNAL_SERVER_ERROR, "ImportIdentifiersError", ex))
          .build();
    }
  }


  /**
   * Get the non-null values of a variable's vector in CSV format.
   *
   * @return
   * @throws org.obiba.magma.MagmaRuntimeException
   * @throws java.io.IOException
   */
  @GET
  @Path("/_export")
  @Produces("text/csv")
  @AuthenticatedByCookie
  @ApiOperation(value = "Get identifiers mapping in CSV", produces = "text/csv")
  public Response getVectorCSVValues(@QueryParam("type") @DefaultValue("Participant") String entityType)
      throws MagmaRuntimeException, IOException {
    ValueTable table = getValueTable(entityType);
    Variable variable = table.getVariable(name);

    ByteArrayOutputStream values = new ByteArrayOutputStream();
    CSVWriter writer = null;
    try {
      writer = new CSVWriter(new PrintWriter(values));
      writeCSVValues(writer, table, variable);
    } finally {
      if(writer != null) writer.close();
    }

    return Response.ok(values.toByteArray(), "text/csv").header("Content-Disposition",
        "attachment; filename=\"" + table.getName() + "-" + variable.getName() + ".csv\"").build();
  }

  /**
   * Get the non-null values of a variable's vector in plain format.
   *
   * @return
   * @throws MagmaRuntimeException
   * @throws IOException
   */
  @GET
  @Path("/_export")
  @Produces("text/plain")
  @AuthenticatedByCookie
  @ApiOperation(value = "Get identifiers in plain text", produces = "text/plain")
  public Response getVectorValues(@QueryParam("type") @DefaultValue("Participant") String entityType)
      throws MagmaRuntimeException, IOException {
    ValueTable table = getValueTable(entityType);
    Variable variable = table.getVariable(name);

    ByteArrayOutputStream values = new ByteArrayOutputStream();
    Writer writer = null;
    try {
      writer = new PrintWriter(values);
      writePlainValues(writer, table);
    } finally {
      if(writer != null) writer.close();
    }

    return Response.ok(values.toByteArray(), "text/plain").header("Content-Disposition",
        "attachment; filename=\"" + table.getName() + "-" + variable.getName() + ".txt\"").build();
  }

  //
  // Private methods
  //

  private Datasource createTransientDatasource(Magma.DatasourceFactoryDto datasourceFactoryDto) {
    DatasourceFactory factory = datasourceFactoryRegistry.parse(datasourceFactoryDto);
    String uid = MagmaEngine.get().addTransientDatasource(factory);

    return MagmaEngine.get().getTransientDatasourceInstance(uid);
  }

  private Iterable<IdentifiersMaps.IdentifiersMap> getUnitIdentifiers(String entityType) {
    return Iterables.filter(new IdentifiersMaps(getValueTable(entityType), name),
        new Predicate<IdentifiersMaps.IdentifiersMap>() {
          @Override
          public boolean apply(@Nullable IdentifiersMaps.IdentifiersMap input) {
            return input.hasPrivateIdentifier();
          }
        });
  }

  @Override
  @NotNull
  protected ValueTable getValueTable(String entityType) {
    ValueTable table = super.getValueTable(entityType);
    if(table == null) throw new NoSuchElementException("No identifiers mapping found for entity type: " + entityType);
    return table;
  }

  private void writeCSVValues(CSVWriter writer, ValueTable table, Variable variable) {
    // header
    writer.writeNext(new String[] { table.getEntityType(), variable.getName() });
    for(IdentifiersMaps.IdentifiersMap unitId : getUnitIdentifiers(table.getEntityType())) {
      writer.writeNext(new String[] { unitId.getSystemIdentifier(), unitId.getPrivateIdentifier() });
    }
  }

  private void writePlainValues(Writer writer, ValueTable table) throws IOException {
    for(IdentifiersMaps.IdentifiersMap unitId : getUnitIdentifiers(table.getEntityType())) {
      writer.write(unitId.getPrivateIdentifier() + "\n");
    }
  }

}