package org.obiba.opal.web.gwt.app.client.administration.identifiers.view;

import org.obiba.opal.web.gwt.app.client.administration.identifiers.presenter.IdentifiersAdministrationPresenter;
import org.obiba.opal.web.gwt.app.client.administration.identifiers.presenter.IdentifiersAdministrationUiHandlers;
import org.obiba.opal.web.model.client.magma.TableDto;

import com.github.gwtbootstrap.client.ui.NavHeader;
import com.github.gwtbootstrap.client.ui.NavLink;
import com.github.gwtbootstrap.client.ui.base.UnorderedList;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class IdentifiersAdministrationView extends ViewWithUiHandlers<IdentifiersAdministrationUiHandlers>
    implements IdentifiersAdministrationPresenter.Display {

  interface Binder extends UiBinder<Widget, IdentifiersAdministrationView> {}

  @UiField
  Panel breadcrumbs;

  @UiField
  UnorderedList selector;

  @UiField
  Panel body;

  private JsArray<TableDto> identifiersTables;

  @Inject
  public IdentifiersAdministrationView(Binder uiBinder) {
    initWidget(uiBinder.createAndBindUi(this));
  }

  @Override
  public void setInSlot(Object slot, IsWidget content) {
    body.clear();
    body.add(content);
  }

  @Override
  public void showIdentifiersTables(JsArray<TableDto> identifiersTables) {
    this.identifiersTables = identifiersTables;
    selector.clear();
    selector.add(new NavHeader("Entity types"));
    for(int i = 0; i < identifiersTables.length(); i++) {
      TableDto table = identifiersTables.get(i);
      NavLink link = new NavLink(table.getEntityType());
      link.addClickHandler(new TableSelectionHandler(i));
      if(i == 0) {
        link.setActive(true);
        getUiHandlers().onSelection(table);
      }
      selector.add(link);
    }
  }

  @Override
  public HasWidgets getBreadcrumbs() {
    return breadcrumbs;
  }

  private class TableSelectionHandler implements ClickHandler {

    private final int i;

    TableSelectionHandler(int i) {
      this.i = i;
    }

    @Override
    public void onClick(ClickEvent event) {
      getUiHandlers().onSelection(identifiersTables.get(i));
    }
  }
}
