/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.web.gwt.app.client.wizard.configureview.view;

import java.util.List;

import org.obiba.opal.web.gwt.app.client.i18n.Translations;
import org.obiba.opal.web.gwt.app.client.widgets.presenter.LabelListPresenter;
import org.obiba.opal.web.gwt.app.client.wizard.configureview.presenter.AttributeDialogPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.HasCloseHandlers;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class AttributeDialogView extends Composite implements AttributeDialogPresenter.Display {

  @UiTemplate("AttributeDialogView.ui.xml")
  interface MyUiBinder extends UiBinder<DialogBox, AttributeDialogView> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  private static Translations translations = GWT.create(Translations.class);

  @UiField
  DialogBox dialog;

  @UiField
  RadioButton nameDropdownRadioChoice;

  @UiField
  RadioButton nameFieldRadioChoice;

  @UiField
  ListBox labels;

  @UiField
  TextBox attributeName;

  @UiField
  ScrollPanel scrollPanel;

  private LabelListPresenter.Display inputField;

  @UiField
  Button saveButton;

  @UiField
  Button cancelButton;

  public AttributeDialogView() {
    initWidget(uiBinder.createAndBindUi(this));
    uiBinder.createAndBindUi(this);
  }

  @Override
  public Widget asWidget() {
    return this;
  }

  @Override
  public void startProcessing() {
  }

  @Override
  public void stopProcessing() {
  }

  @Override
  public void clear() {
    attributeName.setText("");

    if(inputField != null) {
      inputField.clearAttributes();
    }
  }

  @Override
  public void showDialog() {
    dialog.center();
    dialog.show();
  }

  @Override
  public void hideDialog() {
    dialog.hide();
  }

  @Override
  public Button getCancelButton() {
    return cancelButton;
  }

  @Override
  public Button getSaveButton() {
    return saveButton;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public HasCloseHandlers getDialog() {
    return dialog;
  }

  @Override
  public HandlerRegistration addNameDropdownRadioChoiceHandler(ClickHandler handler) {
    return nameDropdownRadioChoice.addClickHandler(handler);
  }

  @Override
  public HandlerRegistration addNameFieldRadioChoiceHandler(ClickHandler handler) {
    return nameFieldRadioChoice.addClickHandler(handler);
  }

  @Override
  public void setLabelsEnabled(boolean enabled) {
    labels.setEnabled(enabled);
  }

  @Override
  public void setAttributeNameEnabled(boolean enabled) {
    attributeName.setEnabled(enabled);
  }

  @Override
  public void selectNameDropdownRadioChoice() {
    nameDropdownRadioChoice.setValue(true, true);
    nameFieldRadioChoice.setValue(false, true);
  }

  public void selectNameFieldRadioChoice() {
    nameDropdownRadioChoice.setValue(false, true);
    nameFieldRadioChoice.setValue(true, true);
  }

  @Override
  public HasText getAttributeNameField() {
    return attributeName;
  }

  @Override
  public void addInputField(LabelListPresenter.Display inputField) {
    scrollPanel.clear();
    scrollPanel.add(inputField.asWidget());
    this.inputField = inputField;
    this.inputField.setAttributeValueLabel(translations.valueLabel());
  }

  @Override
  public void removeInputField() {
    scrollPanel.clear();
    inputField = null;
  }

  @Override
  public HasText getAttributeName() {
    return new HasText() {

      @Override
      public void setText(String arg0) {
      }

      @Override
      public String getText() {
        if(nameDropdownRadioChoice.getValue()) {
          return labels.getValue(labels.getSelectedIndex());
        } else {
          return attributeName.getText();
        }
      }
    };
  }

  @Override
  public void setNameDropdownList(List<String> labels) {
    this.labels.clear();
    for(String label : labels) {
      this.labels.addItem(label, label);
    }
  }

  @Override
  public HasText getCaption() {
    return dialog;
  }

  @Override
  public void setAttributeName(String attributeName) {
    int index = getLabelIndex(attributeName);
    if(index > 0) {
      labels.setItemSelected(index, true);
    } else {
      setLabelsEnabled(false);
      setAttributeNameEnabled(true);
      selectNameFieldRadioChoice();
      this.attributeName.setText(attributeName);
    }
  }

  private int getLabelIndex(String name) {
    for(int i = 0; i < labels.getItemCount(); i++) {
      if(labels.getValue(i).equals(name)) return i;
    }
    return -1;
  }
}
