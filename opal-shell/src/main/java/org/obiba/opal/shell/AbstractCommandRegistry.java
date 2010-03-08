/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.shell;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.obiba.opal.shell.commands.Command;
import org.obiba.opal.shell.commands.CommandUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;
import uk.co.flamingpenguin.jewel.cli.CliFactory;
import uk.co.flamingpenguin.jewel.cli.CommandLineInterface;

import com.google.common.collect.ImmutableSet;

/**
 * An abstract implementation of {@code CommandRegistry}. Extending classes should invoke the {@code
 * addAvailableCommand(Class, Class)} method with all commands that should be made available.
 */
public abstract class AbstractCommandRegistry implements CommandRegistry {
  //
  // Instance Variables
  //

  @SuppressWarnings("unchecked")
  private final Map<String, Class<? extends Command>> commandMap;

  @SuppressWarnings("unchecked")
  private final Map<String, Class> optionsMap;

  @Autowired
  private ApplicationContext ctx;

  //
  // Constructors
  //

  @SuppressWarnings("unchecked")
  public AbstractCommandRegistry() {
    commandMap = new HashMap<String, Class<? extends Command>>();
    optionsMap = new HashMap<String, Class>();
  }

  public Set<String> getAvailableCommands() {
    return ImmutableSet.copyOf(commandMap.keySet());
  }

  public boolean hasCommand(String commandName) {
    return commandMap.containsKey(commandName);
  }

  /**
   * Adds the specified command to the client's command set.
   * 
   * @param commandClass command class
   */
  protected <T> void addAvailableCommand(Class<? extends Command<T>> commandClass, Class<T> optionsClass) {
    if(commandClass == null) throw new IllegalArgumentException("commandClass cannot be null");
    if(optionsClass == null) throw new IllegalArgumentException("optionClass cannot be null");
    if(commandClass.isAnnotationPresent(CommandUsage.class) == false) throw new IllegalArgumentException("command class " + commandClass.getName() + " must be annotated with @CommandUsage");
    if(optionsClass.isAnnotationPresent(CommandLineInterface.class) == false) throw new IllegalArgumentException("options class " + optionsClass.getName() + " must be annotated with @CommandLineInterface");

    CommandLineInterface annotation = optionsClass.getAnnotation(CommandLineInterface.class);
    commandMap.put(annotation.application(), commandClass);
    optionsMap.put(annotation.application(), optionsClass);
  }

  public CommandUsage getCommandUsage(String commandName) {
    return commandMap.get(commandName).getAnnotation(CommandUsage.class);
  }

  public Command<?> newCommand(String commandName, String[] arguments) throws ArgumentValidationException {
    Command<Object> command = null;
    Class<?> commandClass = commandMap.get(commandName);
    if(commandClass == null) {
      throw new IllegalArgumentException("Command not found (" + commandName + ")");
    }

    try {
      // Create the command object.
      command = (Command<Object>) commandClass.newInstance();
      ctx.getAutowireCapableBeanFactory().autowireBeanProperties(command, AutowireCapableBeanFactory.AUTOWIRE_NO, false);

      // Create the options object.
      Class<?> optionsClass = optionsMap.get(commandName);
      // Set the command's options.
      command.setOptions(CliFactory.parseArguments(optionsClass, arguments));
    } catch(ArgumentValidationException e) {
      throw e;
    } catch(Exception e) {
      throw new IllegalArgumentException(e);
    }

    return command;
  }
}