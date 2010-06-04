/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.shell.service;

import java.util.List;

import org.obiba.opal.core.runtime.Service;
import org.obiba.opal.shell.CommandJob;

/**
 * Service for {@link CommandJob} operations.
 */
public interface CommandJobService extends Service {

  /**
   * Assigns an id to a command job and submits it for asynchronous execution.
   * 
   * @param commandJob the submitted command job
   * @return the command job's id
   */
  public Long launchCommand(CommandJob commandJob);

  /**
   * Returns the specified command job.
   * 
   * @param id command job id
   * @return the command job (<code>null</code> if none)
   */
  public CommandJob getCommand(Long id);

  /**
   * Returns the history of launched commands.
   * 
   * @return history of launched commands
   */
  public List<CommandJob> getHistory();

  /**
   * Cancels the specified command job.
   * 
   * @param id command job id
   * @throws NoSuchCommandJobException if the specified command job does not exist
   * @throws IllegalStateException if the command job is not in a "cancellable" state (either NOT_STARTED or
   * IN_PROGRESS) and therefore cannot be cancelled
   */
  public void cancelCommand(Long id) throws NoSuchCommandJobException, IllegalStateException;

  /**
   * Deletes the specified command job.
   * 
   * @param id command job id
   * @throws NoSuchCommandJobException if the specified command job does not exist
   * @throws IllegalStateException if the command job is in a "running" state (IN_PROGRESS or CANCEL_PENDING) and
   * therefore cannot be deleted
   */
  public void deleteCommand(Long id) throws NoSuchCommandJobException, IllegalStateException;
}
