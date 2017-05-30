/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package org.obiba.opal.web.gwt.app.client.search.variables;

import com.github.gwtbootstrap.client.ui.Breadcrumbs;
import com.github.gwtbootstrap.client.ui.TextBox;
import com.github.gwtbootstrap.client.ui.Typeahead;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import org.obiba.opal.web.gwt.app.client.js.JsArrays;
import org.obiba.opal.web.gwt.app.client.ui.CriteriaPanel;
import org.obiba.opal.web.gwt.app.client.ui.OpalSimplePager;
import org.obiba.opal.web.gwt.app.client.ui.Table;
import org.obiba.opal.web.model.client.magma.TableDto;
import org.obiba.opal.web.model.client.opal.TaxonomyDto;
import org.obiba.opal.web.model.client.search.ItemResultDto;
import org.obiba.opal.web.model.client.search.QueryResultDto;

import java.util.List;

public class SearchVariablesView extends ViewWithUiHandlers<SearchVariablesUiHandlers> implements SearchVariablesPresenter.Display {

  interface Binder extends UiBinder<Widget, SearchVariablesView> {}

  private final PlaceManager placeManager;

  @UiField
  Breadcrumbs breadcrumbs;

  @UiField
  CriteriaPanel queryPanel;

  @UiField(provided = true)
  Typeahead queryTypeahead;

  @UiField
  TextBox queryInput;

  @UiField
  Image refreshPending;

  @UiField
  VariableItemTable variableItemTable;

  @UiField
  OpalSimplePager variableItemPager;

  private VariableItemProvider variableItemProvider;

  @Inject
  public SearchVariablesView(SearchVariablesView.Binder uiBinder, PlaceManager placeManager) {
    initQueryTypeahead();
    initWidget(uiBinder.createAndBindUi(this));
    this.placeManager = placeManager;
  }
  
  @Override
  public HasWidgets getBreadcrumbs() {
    return breadcrumbs;
  }

  @UiHandler("searchButton")
  public void onSearch(ClickEvent event) {
    setVariablesVisible(false);
    getUiHandlers().onSearch(getQuery());
  }

  @UiHandler("queryInput")
  public void onQueryTyped(KeyUpEvent event) {
    if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER || getQuery().isEmpty()) onSearch(null);
  }

  @Override
  public void setTaxonomies(List<TaxonomyDto> taxonomies) {
    ((VariableFieldSuggestOracle) queryTypeahead.getSuggestOracle()).setTaxonomies(taxonomies);
  }

  @Override
  public void setTables(List<TableDto> tables) {
    ((VariableFieldSuggestOracle) queryTypeahead.getSuggestOracle()).setTables(tables);
  }

  @Override
  public void setQuery(String query) {
    queryInput.setText(query);
    setVariablesVisible(false);
  }

  @Override
  public void showResults(QueryResultDto results, int offset, int limit) {
    initVariableItemTable();
    variableItemProvider.updateRowData(offset, JsArrays.toList(results.getHitsArray()));
    variableItemProvider.updateRowCount(results.getTotalHits(), true);
    variableItemPager.setPagerVisible(results.getTotalHits() > Table.DEFAULT_PAGESIZE);
    setVariablesVisible(true);
  }

  @Override
  public void clearResults() {
    setVariablesVisible(false);
    refreshPending.setVisible(false);
  }

  @Override
  public void reset() {
    clearResults();
    queryInput.setText("");
  }

  //
  // Private methods
  //

  private String getQuery() {
    String queryDropdowns = queryPanel.getQueryString();
    if ("*".equals(queryDropdowns)) queryDropdowns = "";
    return (queryDropdowns  + " " + queryInput.getText()).trim();
  }

  private void initQueryTypeahead() {
    queryTypeahead = new Typeahead(new VariableFieldSuggestOracle());
    queryTypeahead.setUpdaterCallback(new Typeahead.UpdaterCallback() {
      @Override
      public String onSelection(SuggestOracle.Suggestion selectedSuggestion) {
        VariableFieldDropdown dd = new VariableFieldDropdown((VariableFieldSuggestOracle.VariableFieldSuggestion) selectedSuggestion) {
          @Override
          public void doFilter() {
            onSearch(null);
          }
        };
        dd.addChangeHandler(new ChangeHandler() {
          @Override
          public void onChange(ChangeEvent event) {
            onSearch(null);
          }
        });
        queryPanel.addCriterion(dd, true, false);
        return "";
        //return selectedSuggestion.getReplacementString();
      }
    });
    queryTypeahead.setDisplayItemCount(15);
    queryTypeahead.setMinLength(2);
  }

  private void initVariableItemTable() {
    if (variableItemProvider == null) {
      variableItemProvider = new VariableItemProvider();
      variableItemTable.setPlaceManager(placeManager);
      variableItemPager.setDisplay(variableItemTable);
      variableItemProvider.addDataDisplay(variableItemTable);
    }
  }

  private void setVariablesVisible(boolean visible) {
    refreshPending.setVisible(!visible);
    variableItemTable.setVisible(visible);
    variableItemPager.setVisible(visible);
  }

  private class VariableItemProvider extends AsyncDataProvider<ItemResultDto> {

    @Override
    protected void onRangeChanged(HasData<ItemResultDto> display) {
      Range range = display.getVisibleRange();
      setVariablesVisible(false);
      getUiHandlers().onSearchRange(getQuery(), range.getStart(), range.getLength());
    }
  }

}
