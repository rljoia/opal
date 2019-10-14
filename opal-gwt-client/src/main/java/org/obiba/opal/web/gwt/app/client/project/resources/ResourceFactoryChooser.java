/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.opal.web.gwt.app.client.project.resources;

import org.obiba.opal.web.gwt.app.client.ui.Chooser;
import org.obiba.opal.web.model.client.opal.ResourceFactoryDto;
import org.obiba.opal.web.model.client.opal.ResourceReferenceDto;

import java.util.*;

public class ResourceFactoryChooser extends Chooser {

  private Map<String, ResourceFactoryDto> resourceFactories;

  public void setResourceFactories(final Map<String, ResourceFactoryDto> resourceFactories) {
    this.resourceFactories = resourceFactories;
    clear();
    List<String> keys = new ArrayList<>(resourceFactories.keySet());
    Collections.sort(keys, new Comparator<String>() {
      @Override
      public int compare(String r1, String r2) {
        return resourceFactories.get(r1).getTitle().compareToIgnoreCase(resourceFactories.get(r2).getTitle());
      }
    });
    for (String key : keys) {
      ResourceFactoryDto factory = resourceFactories.get(key);
      addItem(factory.getTitle(), key);
    }
  }

  public Map<String, ResourceFactoryDto> getResourceFactories() {
    return resourceFactories;
  }

  public ResourceFactoryDto getSelectedFactory() {
    return resourceFactories.get(getSelectedValue());
  }

  public void setSelectedFactory(ResourceReferenceDto resource) {
    String key = resource.getProvider() + ":" + resource.getFactory();
    setSelectedValue(key);
  }
}
