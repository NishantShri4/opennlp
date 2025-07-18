/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.cmdline;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.commons.Internal;

/**
 * Parser for command line arguments. The parser creates a dynamic proxy which
 * can be access via a command line argument interface.
 *
 * <p>
 *
 * The command line argument proxy interface must follow these conventions:<br>
 * - Methods do not define arguments<br>
 * - Method names must start with get<br>
 * - Allowed return types are Integer, Boolean, String, File and Charset.<br>
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
@Internal
public class ArgumentParser {

  public @Retention(RetentionPolicy.RUNTIME) @interface OptionalParameter {
    // CHECKSTYLE:OFF
    String DEFAULT_CHARSET = "DEFAULT_CHARSET";
    // CHECKSTYLE:ON
    String defaultValue() default "";
  }

  public @Retention(RetentionPolicy.RUNTIME) @interface ParameterDescription {
    String valueName();
    String description() default "";
  }

  private interface ArgumentFactory {

    String INVALID_ARG = "Invalid argument: %s %s \n";

    Object parseArgument(Method method, String argName, String argValue);
  }

  private static class IntegerArgumentFactory implements ArgumentFactory {

    @Override
    public Object parseArgument(Method method, String argName, String argValue) {

      Object value;

      try {
        value = Integer.parseInt(argValue);
      }
      catch (NumberFormatException e) {
        throw new TerminateToolException(1, String.format(INVALID_ARG, argName, argValue) +
            "Value must be an integer!", e);
      }

      return value;
    }
  }

  private static class BooleanArgumentFactory implements ArgumentFactory {

    @Override
    public Object parseArgument(Method method, String argName, String argValue) {
      return Boolean.parseBoolean(argValue);
    }
  }

  private static class StringArgumentFactory implements ArgumentFactory {

    @Override
    public Object parseArgument(Method method, String argName, String argValue) {
      return argValue;
    }
  }

  private static class FileArgumentFactory implements ArgumentFactory {

    @Override
    public Object parseArgument(Method method, String argName, String argValue) {
      return new File(argValue);
    }
  }

  private static class CharsetArgumentFactory implements ArgumentFactory {

    @Override
    public Object parseArgument(Method method, String argName, String charsetName) {

      try {
        if (OptionalParameter.DEFAULT_CHARSET.equals(charsetName)) {
          return Charset.defaultCharset();
        } else if (Charset.isSupported(charsetName)) {
          return Charset.forName(charsetName);
        } else {
          throw new TerminateToolException(1,  String.format(INVALID_ARG, argName, charsetName) +
              "Encoding not supported on this platform.");
        }
      } catch (IllegalCharsetNameException e) {
        throw new TerminateToolException(1, String.format(INVALID_ARG, argName, charsetName) +
            "Illegal encoding name.");
      }
    }
  }

  private record ArgumentProxy(Map<String, Object> arguments) implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

      if (args != null)
        throw new IllegalStateException();

      return arguments.get(method.getName());
    }
  }

  private static final Map<Class<?>, ArgumentFactory> argumentFactories;

  static {
    Map<Class<?>, ArgumentFactory> factories = new HashMap<>();
    factories.put(Integer.class, new IntegerArgumentFactory());
    factories.put(Boolean.class, new BooleanArgumentFactory());
    factories.put(String.class, new StringArgumentFactory());
    factories.put(File.class, new FileArgumentFactory());
    factories.put(Charset.class, new CharsetArgumentFactory());

    argumentFactories = Collections.unmodifiableMap(factories);
  }

  private ArgumentParser() {
  }

  private static void checkProxyInterfaces(Class<?>... proxyInterfaces) {
    for (Class<?> proxyInterface : proxyInterfaces) {
      if (null != proxyInterface) {
        if (!proxyInterface.isInterface())
          throw new IllegalArgumentException("proxy interface is not an interface!");

        // all checks should also be performed for super interfaces

        Method[] methods = proxyInterface.getMethods();

        if (methods.length == 0)
          throw new IllegalArgumentException("proxy interface must at least declare one method!");

        for (Method method : methods) {

          // check that method names start with get
          if (!method.getName().startsWith("get") && method.getName().length() > 3)
            throw new IllegalArgumentException(method.getName() + " method name does not start with 'get'!");

          // check that method has zero arguments
          if (method.getParameterTypes().length != 0) {
            throw new IllegalArgumentException(method.getName()
                + " method must have zero parameters but has "
                + method.getParameterTypes().length + "!");
          }

          // check return types of interface
          Class<?> returnType = method.getReturnType();

          Set<Class<?>> compatibleReturnTypes = argumentFactories.keySet();

          if (!compatibleReturnTypes.contains(returnType)) {
            throw new IllegalArgumentException(method.getName()
                + " method must have compatible return type! Got "
                + returnType + ", expected one of " + compatibleReturnTypes);
          }
        }
      }
    }
  }

  private static String methodNameToParameter(String methodName) {
    // remove get from method name
    char[] parameterNameChars = methodName.toCharArray();

    // name length is checked to be at least 4 prior
    parameterNameChars[3] = Character.toLowerCase(parameterNameChars[3]);

    return "-" + new String(parameterNameChars).substring(3);
  }

  /**
   * Creates a usage string which can be printed in case the user did specify the arguments
   * incorrectly. Incorrectly is defined as {@link ArgumentParser#validateArguments(String[], Class)}
   * returns {@code false}.
   *
   * @param argProxyInterface interface with parameter descriptions
   * @return the help message usage string
   */
  public static <T> String createUsage(Class<T> argProxyInterface) {
    return createUsage(new Class<?>[]{argProxyInterface});
  }

  /**
   * Auxiliary record that holds information about an argument. This is used by the
   * {@code GenerateManualTool}, which creates a Docbook for the CLI automatically.
   */
  record Argument(String argument, String value, String description, boolean optional) {

  }

  /**
   * Outputs the arguments as a data structure, so it can be used to create documentation.
   *
   * @param argProxyInterfaces interfaces with parameter descriptions
   * @return the help message usage string
   */
  public static List<Argument> createArguments(Class<?>... argProxyInterfaces) {
    checkProxyInterfaces(argProxyInterfaces);

    Set<String> duplicateFilter = new HashSet<>();

    List<Argument> arguments = new LinkedList<>();

    for (Class<?> argProxyInterface : argProxyInterfaces) {
      if (null != argProxyInterface) {
        for (Method method : argProxyInterface.getMethods()) {

          ParameterDescription desc = method.getAnnotation(ParameterDescription.class);

          OptionalParameter optional = method.getAnnotation(OptionalParameter.class);

          if (desc != null) {
            String paramName = methodNameToParameter(method.getName());

            if (duplicateFilter.contains(paramName)) {
              continue;
            }
            else {
              duplicateFilter.add(paramName);
            }

            boolean isOptional = optional != null;

            Argument arg = new Argument(paramName.substring(1),
                desc.valueName(), desc.description(), isOptional);

            arguments.add(arg);

          }
        }
      }
    }

    return arguments;
  }

  /**
   * Creates a usage string which can be printed in case the user did specify the arguments
   * incorrectly. Incorrectly is defined as {@link ArgumentParser#validateArguments(String[],
   * Class[])} returns {@code false}.
   *
   * @param argProxyInterfaces interfaces with parameter descriptions
   * @return the help message usage string
   */
  public static String createUsage(Class<?>... argProxyInterfaces) {
    checkProxyInterfaces(argProxyInterfaces);

    Set<String> duplicateFilter = new HashSet<>();

    StringBuilder usage = new StringBuilder();
    StringBuilder details = new StringBuilder();
    for (Class<?> argProxyInterface : argProxyInterfaces) {
      if (null != argProxyInterface) {
        for (Method method : argProxyInterface.getMethods()) {

          ParameterDescription desc = method.getAnnotation(ParameterDescription.class);

          OptionalParameter optional = method.getAnnotation(OptionalParameter.class);

          if (desc != null) {
            String paramName = methodNameToParameter(method.getName());

            if (duplicateFilter.contains(paramName)) {
              continue;
            }
            else {
              duplicateFilter.add(paramName);
            }

            if (optional != null)
              usage.append('[');

            usage.append(paramName).append(' ').append(desc.valueName());
            details.append('\t').append(paramName).append(' ').append(desc.valueName()).append('\n');
            if (desc.description().length() > 0) {
              details.append("\t\t").append(desc.description()).append('\n');
            }

            if (optional != null)
              usage.append(']');

            usage.append(' ');
          }
        }
      }
    }

    if (!usage.isEmpty())
      usage.setLength(usage.length() - 1);

    if (!details.isEmpty()) {
      details.setLength(details.length() - 1);
      usage.append("\n\nArguments description:\n").append(details);
    }

    return usage.toString();
  }

  /**
   * Tests if the argument are correct or incorrect. Incorrect means, that mandatory arguments are missing or
   * there are unknown arguments. The argument value itself can also be incorrect, but this
   * is checked by the {@link ArgumentParser#parse(String[], Class)} method and reported accordingly.
   *
   * @param args command line arguments
   * @param argProxyInterface interface with parameters description
   * @return {@code true} if arguments are valid, {@code false otherwise}
   */
  public static <T> boolean validateArguments(String[] args, Class<T> argProxyInterface) {
    return validateArguments(args, new Class<?>[]{argProxyInterface});
  }

  /**
   * Tests if the argument are correct or incorrect. Incorrect means, that mandatory arguments are missing or
   * there are unknown arguments. The argument value itself can also be incorrect, but this
   * is checked by the {@link ArgumentParser#parse(String[], Class)} method and reported accordingly.
   *
   * @param args command line arguments
   * @param argProxyInterfaces interfaces with parameters description
   * @return {@code true} if arguments are valid, {@code false otherwise}
   */
  public static boolean validateArguments(String[] args, Class<?>... argProxyInterfaces) {
    return null == validateArgumentsLoudly(args, argProxyInterfaces);
  }

  /**
   * Tests if the arguments are correct or incorrect.
   *
   * @param args command line arguments
   * @param argProxyInterface interface with parameters description
   * @return {@code null}, if arguments are valid or error message otherwise
   */
  public static String validateArgumentsLoudly(String[] args, Class<?> argProxyInterface) {
    return validateArgumentsLoudly(args, new Class<?>[]{argProxyInterface});
  }

  /**
   * Tests if the arguments are correct or incorrect.
   *
   * @param args command line arguments
   * @param argProxyInterfaces interfaces with parameters description
   * @return {@code null}, if arguments are valid or error message otherwise
   */
  public static String validateArgumentsLoudly(String[] args, Class<?>... argProxyInterfaces) {
    // number of parameters must be always be even
    if (args.length % 2 != 0) {
      return "Number of parameters must be always be even";
    }

    int argumentCount = 0;
    List<String> parameters = new ArrayList<>(Arrays.asList(args));

    for (Class<?> argProxyInterface : argProxyInterfaces) {
      for (Method method : argProxyInterface.getMethods()) {
        String paramName = methodNameToParameter(method.getName());
        int paramIndex = getParameterIndex(paramName, args);
        String valueString = getParameter(paramName, args);
        if (valueString == null) {
          OptionalParameter optionalParam = method.getAnnotation(OptionalParameter.class);

          if (optionalParam == null) {
            if (-1 < paramIndex) {
              return "Missing mandatory parameter value: " + paramName;
            } else {
              return "Missing mandatory parameter: " + paramName;
            }
          } else {
            parameters.remove("-" + paramName);
          }
        }
        else {
          parameters.remove(paramName);
          parameters.remove(valueString);
          argumentCount++;
        }
      }
    }

    if (args.length / 2 > argumentCount) {
      return "Unrecognized parameters encountered: " + parameters;
    }

    return null;
  }

  /**
   * Parses the passed arguments and creates an instance of the proxy interface.
   * <p>
   * In case an argument value cannot be parsed a {@link TerminateToolException} is
   * thrown which contains an error message which explains the problems.
   *
   * @param args arguments
   * @param argProxyInterface interface with parameters description
   *
   * @return parsed parameters
   *
   * @throws TerminateToolException if an argument value cannot be parsed.
   * @throws IllegalArgumentException if validateArguments returns {@code false},
   *     if the proxy interface is not compatible.
   */
  @SuppressWarnings("unchecked")
  public static <T> T parse(String[] args, Class<T> argProxyInterface) {

    checkProxyInterfaces(argProxyInterface);

    if (!validateArguments(args, argProxyInterface))
      throw new IllegalArgumentException("Passed args must be valid!");

    Map<String, Object> arguments = new HashMap<>();

    for (Method method : argProxyInterface.getMethods()) {

      String parameterName = methodNameToParameter(method.getName());
      String valueString = getParameter(parameterName, args);

      if (valueString == null) {
        OptionalParameter optionalParam = method.getAnnotation(OptionalParameter.class);

        if (optionalParam.defaultValue().length() > 0)
          valueString = optionalParam.defaultValue();
      }

      Class<?> returnType = method.getReturnType();

      Object value;

      if (valueString != null) {
        ArgumentFactory factory = argumentFactories.get(returnType);

        if (factory == null)
          throw new IllegalStateException("factory for '" + returnType + "' must not be null");

        value = factory.parseArgument(method, parameterName, valueString);
      }
      else
        value = null;

      arguments.put(method.getName(), value);
    }

    return (T) java.lang.reflect.Proxy.newProxyInstance(
        argProxyInterface.getClassLoader(),
        new Class<?>[]{argProxyInterface},
        new ArgumentProxy(arguments));
  }

  /**
   * Filters arguments leaving only those pertaining to {@code argProxyInterface}.
   *
   * @param args arguments
   * @param argProxyInterface interface with parameters description
   * @param <T> T
   * @return arguments pertaining to {@code argProxyInterface}
   */
  public static <T> String[] filter(String[] args, Class<T> argProxyInterface) {
    ArrayList<String> parameters = new ArrayList<>(args.length);

    for (Method method : argProxyInterface.getMethods()) {

      String parameterName = methodNameToParameter(method.getName());
      int idx = getParameterIndex(parameterName, args);
      if (-1 < idx) {
        parameters.add(parameterName);
        String valueString = getParameter(parameterName, args);
        if (null != valueString) {
          parameters.add(valueString);
        }
      }
    }

    return parameters.toArray(new String[0]);
  }

  /**
   * Retrieves the specified parameter from the specified arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  private static Integer getIntParameter(String param, String[] args) {
    String value = getParameter(param, args);

    try {
      if (value != null)
        return Integer.parseInt(value);
    }
    catch (NumberFormatException ignored) {
      // in this case return null
    }

    return null;
  }

  /**
   * Retrieves the specified parameter from the given arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  private static String getParameter(String param, String[] args) {
    int i = getParameterIndex(param, args);
    if (-1 < i) {
      i++;
      if (i < args.length) {
        return args[i];
      }
    }

    return null;
  }

  /**
   * Returns the index of the parameter in the arguments, or {@code -1} if the parameter is not found.
   *
   * @param param parameter name
   * @param args arguments
   * @return the index of the parameter in the arguments, or {@code -1} if the parameter is not found
   */
  private static int getParameterIndex(String param, String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("-") && args[i].equals(param)) {
        return i;
      }
    }

    return -1;
  }
}
