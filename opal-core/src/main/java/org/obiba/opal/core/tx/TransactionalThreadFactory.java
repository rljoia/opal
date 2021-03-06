/*
 * Copyright (c) 2019 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.obiba.opal.core.tx;

import java.util.concurrent.ThreadFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionalThreadFactory implements ThreadFactory {

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Override
  public Thread newThread(Runnable runnable) {
    return new TransactionalThread(transactionTemplate, runnable);
  }

}
