package org.obiba.opal.shell;

import java.util.Set;

import org.obiba.opal.shell.commands.Command;
import org.obiba.opal.shell.commands.CommandUsage;

import uk.co.flamingpenguin.jewel.cli.ArgumentValidationException;

/**
 * A registry of commands made available to a {@code OpalShell}. Consider extending {@code AbstractCommandRegistry}
 * instead of implementing this interface directly.
 */
public interface CommandRegistry {

  /**
   * Returns true if this registry has a command with the specified name.
   * @param name
   * @return
   */
  boolean hasCommand(String name);

  /**
   * Returns the set of available command names.
   * @return
   */
  Set<String> getAvailableCommands();

  /**
   * Retrieves the CommandUsage annotation for a command name.
   * 
   * @param commandName The command name.
   */
  CommandUsage getCommandUsage(String name);

  /**
   * Returns a new instance of the specified command using the specified arguments.
   * 
   * @param name
   * @param arguments
   * @return
   * @throws ArgumentValidationException
   */
  Command<?> newCommand(String name, String[] arguments) throws ArgumentValidationException;

}
