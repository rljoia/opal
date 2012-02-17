/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.web.gwt.app.client.wizard.importdata.view;

import org.obiba.opal.web.gwt.app.client.event.NotificationEvent;
import org.obiba.opal.web.gwt.app.client.i18n.Translations;
import org.obiba.opal.web.gwt.app.client.validator.ValidationHandler;
import org.obiba.opal.web.gwt.app.client.wizard.WizardStepChain;
import org.obiba.opal.web.gwt.app.client.wizard.WizardStepController.StepInHandler;
import org.obiba.opal.web.gwt.app.client.wizard.WizardStepController.WidgetProvider;
import org.obiba.opal.web.gwt.app.client.wizard.WizardStepDisplay;
import org.obiba.opal.web.gwt.app.client.wizard.importdata.ImportFormat;
import org.obiba.opal.web.gwt.app.client.wizard.importdata.presenter.ConclusionStepPresenter;
import org.obiba.opal.web.gwt.app.client.wizard.importdata.presenter.DataImportPresenter;
import org.obiba.opal.web.gwt.app.client.wizard.importdata.presenter.DataImportPresenter.ImportDataInputsHandler;
import org.obiba.opal.web.gwt.app.client.workbench.view.WizardDialogBox;
import org.obiba.opal.web.gwt.app.client.workbench.view.WizardStep;
import org.obiba.opal.web.model.client.magma.DatasourceParsingErrorDto.ClientErrorDtoExtensions;
import org.obiba.opal.web.model.client.ws.ClientErrorDto;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.PopupViewImpl;

public class DataImportView extends PopupViewImpl implements DataImportPresenter.Display {

  @UiTemplate("DataImportView.ui.xml")
  interface ViewUiBinder extends UiBinder<Widget, DataImportView> {
  }

  private static ViewUiBinder uiBinder = GWT.create(ViewUiBinder.class);

  private static Translations translations = GWT.create(Translations.class);

  private final Widget widget;

  @UiField
  WizardDialogBox dialog;

  @UiField
  WizardStep formatSelectionStep;

  @UiField
  WizardStep formatStep;

  @UiField
  WizardStep destinationSelectionStep;

  @UiField
  WizardStep comparedDatasourcesReportStep;

  @UiField
  SimplePanel comparedDatasourcesReportPanel;

  @UiField
  ValidationReportStepView validationReportPanel;

  @UiField
  WizardStep valuesStep;

  @UiField
  WizardStep identityArchiveStep;

  @UiField
  WizardStep conclusionStep;

  @UiField
  HTMLPanel formatSelectionHelp;

  @UiField
  HTMLPanel destinationSelectionHelp;

  @UiField
  ListBox formatListBox;

  private final EventBus eventBus;

  private WizardStepDisplay formatStepDisplay;

  private WizardStepDisplay identityArchiveStepDisplay;

  private WizardStepChain stepChain;

  private StepInHandler comparedDatasourcesReportStepInHandler;

  private Widget comparedDatasourcesReportHelp;

  private ImportDataInputsHandler importDataInputsHandler;

  @Inject
  public DataImportView(EventBus eventBus) {
    super(eventBus);
    this.eventBus = eventBus;
    widget = uiBinder.createAndBindUi(this);
    initWidgets();
    initWizardDialog();
  }

  private void initWizardDialog() {
    stepChain = WizardStepChain.Builder.create(dialog)//
    .append(formatSelectionStep, formatSelectionHelp)//
    .title(translations.dataImportFormatStep())//

    .append(formatStep)//
    .help(new WidgetProvider() {

      @Override
      public Widget getWidget() {
        return formatStepDisplay.getStepHelp();
      }
    }) //
    .onValidate(new ValidationHandler() {

      @Override
      public boolean validate() {
        return importDataInputsHandler.validateFormat();
      }
    }).title(translations.dataImportFileStep())//

    .append(destinationSelectionStep, destinationSelectionHelp)//
    .title(translations.dataImportDestinationStep())//
    .onValidate(new ValidationHandler() {

      @Override
      public boolean validate() {
        return importDataInputsHandler.validateDestination();
      }
    })//

    .append(comparedDatasourcesReportStep)//
    .title(translations.dataImportComparedDatasourcesReportStep())//
    .help(new WidgetProvider() {

      @Override
      public Widget getWidget() {
        return comparedDatasourcesReportHelp;
      }
    })//
    .onStepIn(new StepInHandler() {

      @Override
      public void onStepIn() {
        comparedDatasourcesReportStepInHandler.onStepIn();
      }
    })//
    .onValidate(new ValidationHandler() {

      @Override
      public boolean validate() {
        return importDataInputsHandler.validateComparedDatasourcesReport();
      }
    })//

    .append(valuesStep)//
    .title(translations.dataImportValuesStep())//

    .append(identityArchiveStep)//
    .title(translations.dataImportUnitStep())//
    .help(new WidgetProvider() {

      @Override
      public Widget getWidget() {
        return identityArchiveStepDisplay.getStepHelp();
      }
    }) //

    .append(conclusionStep)//
    .conclusion()//

    .onNext().onPrevious().build();

  }

  private void initWidgets() {
    formatListBox.addItem(translations.csvLabel(), ImportFormat.CSV.name());
    formatListBox.addItem(translations.opalXmlLabel(), ImportFormat.XML.name());
  }

  @Override
  public Widget asWidget() {
    return widget;
  }

  @Override
  protected PopupPanel asPopupPanel() {
    return dialog;
  }

  @Override
  public void setInSlot(Object slot, Widget content) {
    if(slot == Slots.Values) {
      valuesStep.removeStepContent();
      valuesStep.add(content);
    }
  }

  @Override
  public ImportFormat getImportFormat() {
    String formatString = formatListBox.getValue(formatListBox.getSelectedIndex());
    return ImportFormat.valueOf(formatString);
  }

  @Override
  public HandlerRegistration addCancelClickHandler(ClickHandler handler) {
    return dialog.addCancelClickHandler(handler);
  }

  @Override
  public HandlerRegistration addCloseClickHandler(ClickHandler handler) {
    return dialog.addCloseClickHandler(handler);
  }

  @Override
  public void show() {
    stepChain.reset();
    super.show();
  }

  @Override
  public HandlerRegistration addFormatChangeHandler(ChangeHandler handler) {
    return formatListBox.addChangeHandler(handler);
  }

  @Override
  public void setFormatStepDisplay(WizardStepDisplay display) {
    this.formatStepDisplay = display;
    formatStep.removeStepContent();
    formatStep.add(display.asWidget());
  }

  @Override
  public void setImportDataInputsHandler(ImportDataInputsHandler handler) {
    this.importDataInputsHandler = handler;
  }

  @Override
  public void setIdentityArchiveStepDisplay(WizardStepDisplay display) {
    this.identityArchiveStepDisplay = display;
    identityArchiveStep.removeStepContent();
    identityArchiveStep.add(display.asWidget());
  }

  @Override
  public void setDestinationSelectionDisplay(WizardStepDisplay display) {
    destinationSelectionStep.removeStepContent();
    destinationSelectionStep.add(display.asWidget());
  }

  @Override
  public void setComparedDatasourcesReportStepInHandler(StepInHandler handler) {
    this.comparedDatasourcesReportStepInHandler = handler;
  }

  @Override
  public void setComparedDatasourcesReportDisplay(WizardStepDisplay display) {
    comparedDatasourcesReportPanel.clear();
    comparedDatasourcesReportPanel.add(display.asWidget());
    comparedDatasourcesReportPanel.setVisible(true);
    validationReportPanel.setVisible(false);
    comparedDatasourcesReportHelp = display.getStepHelp();
  }

  @Override
  public HandlerRegistration addFinishClickHandler(final ClickHandler handler) {
    return dialog.addFinishClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent evt) {
        // asynchronous next, see setConclusion()
        handler.onClick(evt);
      }
    });
  }

  @Override
  public void renderConclusion(ConclusionStepPresenter presenter) {
    conclusionStep.removeStepContent();
    presenter.reset();
    conclusionStep.add(presenter.getDisplay().asWidget());
    conclusionStep.setStepTitle(translations.dataImportPendingValidation());
    stepChain.onNext();
    dialog.setCancelEnabled(false);
    dialog.setCloseEnabled(true);
  }

  @Override
  public void showDatasourceCreationError(ClientErrorDto errorDto) {
    comparedDatasourcesReportPanel.setVisible(false);
    if(errorDto.getExtension(ClientErrorDtoExtensions.errors) != null) {
      validationReportPanel.showDatasourceParsingErrors(errorDto);
      validationReportPanel.setVisible(true);
    } else {
      eventBus.fireEvent(NotificationEvent.newBuilder().error(errorDto.getStatus()).args(errorDto.getArgumentsArray()).build());
    }
    dialog.setNextEnabled(false);
  }

  @Override
  public void showDatasourceCreationSuccess() {
    comparedDatasourcesReportPanel.setVisible(true);
    validationReportPanel.setVisible(false);
    dialog.setNextEnabled(true);
  }

}
