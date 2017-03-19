/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.opal.shell.commands;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.obiba.opal.core.domain.Project;
import org.obiba.opal.core.runtime.NoSuchServiceException;
import org.obiba.opal.core.runtime.OpalRuntime;
import org.obiba.opal.core.service.ProjectService;
import org.obiba.opal.shell.commands.options.ImportVCFCommandOptions;
import org.obiba.opal.spi.ServicePlugin;
import org.obiba.opal.spi.vcf.VCFStore;
import org.obiba.opal.spi.vcf.VCFStoreException;
import org.obiba.opal.spi.vcf.VCFStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

@CommandUsage(description = "Import VCF file into a project.",
    syntax = "Syntax: import-vcf --project PROJECT --name NAME --file FILE")
public class ImportVCFCommand extends AbstractOpalRuntimeDependentCommand<ImportVCFCommandOptions> {

  private static final Logger log = LoggerFactory.getLogger(ImportVCFCommand.class);

  @Autowired
  private ProjectService projectService;

  @Autowired
  private OpalRuntime opalRuntime;

  private VCFStore store;

  //
  // AbstractOpalRuntimeDependentCommand Methods
  //

  @Override
  public int execute() {
    Stopwatch stopwatch = Stopwatch.createStarted();

    getShell().printf("Importing VCF/BCF files in project '%s'...", options.getProject());
    getShell().progress(String.format("Preparing VCF file store for project '%s'", options.getProject()), 0, 3, 0);
    Project project = projectService.getProject(getOptions().getProject());
    if (!opalRuntime.hasServicePlugins()) throw new NoSuchServiceException(VCFStoreService.SERVICE_TYPE);
    if (!project.hasVCFStoreService()) {
      // for now get the first one. Some day, the service type will be a project admin choice
      ServicePlugin service = opalRuntime.getServicePlugins()
          .stream().filter(s -> opalRuntime.isVCFStorePluginService(s)).iterator().next();
      project.setVCFStoreService(service.getName());
      projectService.save(project);

    }
    setVCFStore(project.getVCFStoreService(), project.getName());

    try {
      importVCF();
    } catch (Exception e) {
      log.error("Cannot import VCF/BCF files in project {}", options.getProject(), e);
      getShell().printf("Cannot import VCF/BCF file: %s", e.getMessage());
      log.info("Import VCF/BCF files failed in {}", stopwatch.stop());
      return 1;
    }

    log.info("Import VCF/BCF files succeeded in {}", stopwatch.stop());
    return 0;
  }

  //
  // Methods
  //

  private void setVCFStore(String serviceName, String name) {
    if (!opalRuntime.hasServicePlugins()) throw new NoSuchElementException("No service plugin is available");
    ServicePlugin servicePlugin = opalRuntime.getServicePlugin(serviceName);
    if (!opalRuntime.isVCFStorePluginService(servicePlugin)) throw new NoSuchElementException("No VCF store service is available");

    VCFStoreService service = (VCFStoreService) servicePlugin;
    if (!service.hasStore(name)) service.createStore(name);
    store = service.getStore(name);
  }

  private void importVCF() throws IOException, VCFStoreException {
    int total = options.getFiles().size() + 1;
    int count = 1;
    for (String vcfFilePath : options.getFiles()) {
      getShell().printf(String.format("Importing VCF/BCF file: %s", vcfFilePath));
      getShell().progress(String.format("Importing VCF/BCF file: %s", vcfFilePath), count, total, (count*100)/total);
      FileObject vcfFileObject = resolveFileInFileSystem(vcfFilePath);
      if (vcfFileObject == null || !vcfFileObject.exists() || vcfFileObject.getType() == FileType.FOLDER)
        throw new IllegalArgumentException("Not a valid path to VCF/BCF file: " + vcfFilePath);
      if (!vcfFileObject.isReadable())
        throw new IllegalArgumentException("VCF/BCF file is not readable: " + vcfFilePath);
      File vcfFile = opalRuntime.getFileSystem().getLocalFile(vcfFileObject);
      store.writeVCF(vcfFile.getName(), new FileInputStream(vcfFile));
      count++;
    }
    getShell().progress(String.format("VCF/BCF file(s) import completed."), total, total, 100);
  }

  FileObject resolveFileInFileSystem(String path) throws FileSystemException {
    if (Strings.isNullOrEmpty(path)) return null;
    return opalRuntime.getFileSystem().getRoot().resolveFile(path);
  }

  @Override
  public String toString() {
    return "import-vcf -p '" + getOptions().getProject() + "' " + String.join(", ", options.getFiles());
  }

}
