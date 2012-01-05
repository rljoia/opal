/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.web.gwt.app.client.navigator.presenter;

import net.customware.gwt.presenter.client.widget.WidgetDisplay;

import org.obiba.opal.web.gwt.app.client.authz.presenter.AclRequest;
import org.obiba.opal.web.gwt.app.client.authz.presenter.AuthorizationPresenter;
import org.obiba.opal.web.gwt.app.client.js.JsArrays;
import org.obiba.opal.web.gwt.app.client.navigator.event.SiblingVariableSelectionEvent;
import org.obiba.opal.web.gwt.app.client.navigator.event.SiblingVariableSelectionEvent.Direction;
import org.obiba.opal.web.gwt.app.client.navigator.event.TableSelectionChangeEvent;
import org.obiba.opal.web.gwt.app.client.navigator.event.VariableSelectionChangeEvent;
import org.obiba.opal.web.gwt.app.client.navigator.event.ViewConfigurationRequiredEvent;
import org.obiba.opal.web.gwt.app.client.widgets.event.SummaryRequiredEvent;
import org.obiba.opal.web.gwt.app.client.widgets.presenter.SummaryTabPresenter;
import org.obiba.opal.web.gwt.app.client.wizard.WizardType;
import org.obiba.opal.web.gwt.app.client.wizard.event.WizardRequiredEvent;
import org.obiba.opal.web.gwt.rest.client.ResourceAuthorizationRequestBuilderFactory;
import org.obiba.opal.web.gwt.rest.client.ResourceCallback;
import org.obiba.opal.web.gwt.rest.client.ResourceRequestBuilderFactory;
import org.obiba.opal.web.gwt.rest.client.authorization.CompositeAuthorizer;
import org.obiba.opal.web.gwt.rest.client.authorization.HasAuthorization;
import org.obiba.opal.web.model.client.magma.AttributeDto;
import org.obiba.opal.web.model.client.magma.CategoryDto;
import org.obiba.opal.web.model.client.magma.TableDto;
import org.obiba.opal.web.model.client.magma.VariableDto;
import org.obiba.opal.web.model.client.magma.ViewDto;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;

public class VariablePresenter extends Presenter<VariablePresenter.Display, VariablePresenter.Proxy> {

  private final SummaryTabPresenter summaryTabPresenter;

  private final AuthorizationPresenter authorizationPresenter;

  private VariableDto variable;

  private ValuesTablePresenter valuesTablePresenter;

  /**
   * @param display
   * @param eventBus
   */
  @Inject
  public VariablePresenter(Display display, EventBus eventBus, Proxy proxy, ValuesTablePresenter valuesTablePresenter, SummaryTabPresenter summaryTabPresenter, AuthorizationPresenter authorizationPresenter) {
    super(eventBus, display, proxy);
    this.valuesTablePresenter = valuesTablePresenter;
    this.summaryTabPresenter = summaryTabPresenter;
    this.authorizationPresenter = authorizationPresenter;
  }

  @Override
  protected void revealInParent() {
    RevealContentEvent.fire(this, NavigatorPresenter.CENTER_PANE, this);
  }

  @ProxyEvent
  public void onVariableSelectionChanged(VariableSelectionChangeEvent e) {
    forceReveal();
  }

  @Override
  protected void onBind() {
    authorizationPresenter.bind();
    valuesTablePresenter.bind();
    getView().setPermissionsTabWidget(authorizationPresenter.getDisplay());
    getView().setValuesDisplay(valuesTablePresenter.getDisplay());

    super.registerHandler(getEventBus().addHandler(VariableSelectionChangeEvent.getType(), new VariableSelectionHandler()));
    summaryTabPresenter.bind();
    getView().setParentCommand(new ParentCommand());
    getView().setNextCommand(new NextCommand());
    getView().setPreviousCommand(new PreviousCommand());
    getView().setSummaryTabCommand(new SummaryCommand());
    getView().setSummaryTabWidget(summaryTabPresenter.getDisplay());
    // TODO
    getView().setDeriveCategorizeCommand(new DeriveCategorizeCommand());
    getView().setDeriveCustomCommand(new DeriveCustomCommand());
  }

  @Override
  protected void onUnbind() {
    authorizationPresenter.unbind();
    summaryTabPresenter.unbind();
    valuesTablePresenter.unbind();
  }

  private void updateDisplay(TableDto table, VariableDto variableDto, VariableDto previous, VariableDto next) {
    if(variable == null || !isCurrentVariable(variableDto)) {
      variable = variableDto;
      getView().setVariableName(variable.getName());
      getView().setEntityType(variable.getEntityType());
      getView().setValueType(variable.getValueType());
      getView().setMimeType(variable.hasMimeType() ? variable.getMimeType() : "");
      getView().setUnit(variable.hasUnit() ? variable.getUnit() : "");
      getView().setRepeatable(variable.getIsRepeatable());
      getView().setOccurrenceGroup(variable.getIsRepeatable() ? variable.getOccurrenceGroup() : "");

      getView().setParentName(variable.getParentLink().getRel());
      getView().setPreviousName(previous != null ? previous.getName() : "");
      getView().setNextName(next != null ? next.getName() : "");

      getView().renderCategoryRows(variable.getCategoriesArray());
      getView().renderAttributeRows(variable.getAttributesArray());
      getView().setCategorizeMenuAvailable(!variable.getValueType().equals("binary"));

      updateDerivedVariableDisplay(table);

      authorize(table);
    }
  }

  private void updateDerivedVariableDisplay(TableDto table) {
    // if table is a view, check for a script attribute
    getView().setDerivedVariable(false, "");
    if(table == null || !table.hasViewLink()) return;

    for(AttributeDto attr : JsArrays.toIterable(variable.getAttributesArray())) {
      if(attr.getName().equals("script")) {
        getView().setDerivedVariable(true, attr.getValue());
        getView().setEditCommand(new EditCommand());
        break;
      }
    }
  }

  private void authorize(TableDto table) {
    // summary
    ResourceAuthorizationRequestBuilderFactory.newBuilder().forResource(variable.getLink() + "/summary").get().authorize(new CompositeAuthorizer(getView().getSummaryAuthorizer(), new SummaryUpdate())).send();

    // edit variable
    if(table.hasViewLink()) {
      ResourceAuthorizationRequestBuilderFactory.newBuilder().forResource(table.getViewLink()).put().authorize(getView().getEditAuthorizer()).send();
    }

    // set permissions
    AclRequest.newResourceAuthorizationRequestBuilder().authorize(new CompositeAuthorizer(getView().getPermissionsAuthorizer(), new PermissionsUpdate())).send();
  }

  private boolean isCurrentVariable(VariableDto variableDto) {
    return variableDto.getName().equals(variable.getName()) && variableDto.getParentLink().getRel().equals(variable.getParentLink().getRel());
  }

  /**
   * @param selection
   */
  private void requestSummary(final VariableDto selection) {
    getEventBus().fireEvent(new SummaryRequiredEvent(selection.getLink() + "/summary"));
  }

  private String getViewLink() {
    return variable.getParentLink().getLink().replaceFirst("/table/", "/view/");
  }

  //
  // Interfaces and classes
  //

  final class DeriveCategorizeCommand implements Command {
    @Override
    public void execute() {
      getEventBus().fireEvent(new WizardRequiredEvent(WizardType.DERIVE_CATEGORIZE_VARIABLE, variable));
    }
  }

  final class DeriveCustomCommand implements Command {
    @Override
    public void execute() {
      getEventBus().fireEvent(new WizardRequiredEvent(WizardType.DERIVE_CUSTOM_VARIABLE, variable));
    }
  }

  /**
   * Update permissions on authorization.
   */
  private final class PermissionsUpdate implements HasAuthorization {
    @Override
    public void unauthorized() {

    }

    @Override
    public void beforeAuthorization() {

    }

    @Override
    public void authorized() {
      authorizationPresenter.setAclRequest(AclRequest.newBuilder("View", variable.getLink(), "GET:GET"), AclRequest.newBuilder("Summary", variable.getLink() + "/summary", "GET:GET"));
      authorizationPresenter.refreshDisplay();
    }
  }

  /**
   * Update summary on authorization.
   */
  private final class SummaryUpdate implements HasAuthorization {
    @Override
    public void unauthorized() {

    }

    @Override
    public void beforeAuthorization() {

    }

    @Override
    public void authorized() {
      requestSummary(variable);
      if(getView().isSummaryTabSelected()) {
        summaryTabPresenter.refreshDisplay();
      }
    }
  }

  /**
   *
   */
  final class PreviousCommand implements Command {
    @Override
    public void execute() {
      getEventBus().fireEvent(new SiblingVariableSelectionEvent(variable, Direction.PREVIOUS));
    }
  }

  /**
   *
   */
  final class NextCommand implements Command {
    @Override
    public void execute() {
      getEventBus().fireEvent(new SiblingVariableSelectionEvent(variable, Direction.NEXT));
    }
  }

  /**
   *
   */
  final class ParentCommand implements Command {
    @Override
    public void execute() {
      ResourceRequestBuilderFactory.<TableDto> newBuilder().forResource(variable.getParentLink().getLink()).get().withCallback(new ResourceCallback<TableDto>() {
        @Override
        public void onResource(Response response, TableDto resource) {
          getEventBus().fireEvent(new TableSelectionChangeEvent(VariablePresenter.this, resource));
        }

      }).send();
    }
  }

  /**
   *
   */
  final class SummaryCommand implements Command {
    @Override
    public void execute() {
      summaryTabPresenter.refreshDisplay();
    }
  }

  final class EditCommand implements Command {
    @Override
    public void execute() {

      ResourceRequestBuilderFactory.<ViewDto> newBuilder().forResource(getViewLink()).get().withCallback(new ResourceCallback<ViewDto>() {

        @Override
        public void onResource(Response response, ViewDto viewDto) {
          getEventBus().fireEvent(new ViewConfigurationRequiredEvent(viewDto, variable));
        }
      }).send();
    }
  }

  class VariableSelectionHandler implements VariableSelectionChangeEvent.Handler {
    @Override
    public void onVariableSelectionChanged(VariableSelectionChangeEvent event) {
      updateDisplay(event.getTable(), event.getSelection(), event.getPrevious(), event.getNext());

    }
  }

  @ProxyStandard
  public interface Proxy extends com.gwtplatform.mvp.client.proxy.Proxy<VariablePresenter> {
  }

  public interface Display extends View {

    void setVariableName(String name);

    void setValuesDisplay(ValuesTablePresenter.Display display);

    void setCategorizeMenuAvailable(boolean available);

    void setDerivedVariable(boolean derived, String script);

    void setEntityType(String text);

    void setValueType(String text);

    void setMimeType(String text);

    void setUnit(String text);

    void setRepeatable(boolean repeatable);

    void setOccurrenceGroup(String text);

    void setParentName(String name);

    void setPreviousName(String name);

    void setNextName(String name);

    void setParentCommand(Command cmd);

    void setNextCommand(Command cmd);

    void setPreviousCommand(Command cmd);

    void renderCategoryRows(JsArray<CategoryDto> rows);

    void renderAttributeRows(JsArray<AttributeDto> rows);

    void setSummaryTabCommand(Command cmd);

    boolean isSummaryTabSelected();

    void setSummaryTabWidget(WidgetDisplay widget);

    HasAuthorization getSummaryAuthorizer();

    HasAuthorization getEditAuthorizer();

    HasAuthorization getPermissionsAuthorizer();

    void setPermissionsTabWidget(AuthorizationPresenter.Display display);

    void setEditCommand(Command cmd);

    void setDeriveCategorizeCommand(Command cmd);

    void setDeriveCustomCommand(Command cmd);
  }
}
