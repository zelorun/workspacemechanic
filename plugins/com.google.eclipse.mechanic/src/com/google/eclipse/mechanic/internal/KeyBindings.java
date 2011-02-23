/*******************************************************************************
 * Copyright (C) 2010, Google Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package com.google.eclipse.mechanic.internal;

import com.google.eclipse.mechanic.core.keybinding.KeyBindingSpec;
import com.google.gson.Gson;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.Scheme;
import org.eclipse.jface.bindings.keys.KeyBinding;
import org.eclipse.jface.bindings.keys.KeySequence;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model for keyboard binding configuration.
 *
 * @author zorzella@google.com
 * @author konigsberg@google.com
 */
public class KeyBindings {

  /*
   * If you're trying to interpret key bindings, this comment block will be useful.
   *
   * There are two kinds of bindings. USER and SYSTEM. You'll see we've split out user
   * and system bindings in the model implementation.
   *
   * One of the tricks of managing keyboard bindings is that of removing a system binding. To
   * remove a system binding from (scheme, triggerSequence) that to (command, params),
   * we create a 'user' binding from (scheme, triggerSequence) to a null command.
   * The system binding still exists, we've just created a user binding that overrides it.
   *
   * This has some implications for both adding and removing bindings, discussed further, below.
   */

  private static final boolean DEBUG = true;

  private final List<Binding> userBindings;
  private final List<Binding> systemBindings;

  /**
   * Creates a new instance from a defined set of bindings.
   */
  public KeyBindings(Binding[] bindings) {
    List<Binding> ub = new ArrayList<Binding>();
    List<Binding> sb = new ArrayList<Binding>();

    for (Binding binding : bindings) {
      if (binding.getType() == Binding.USER) {
        ub.add(binding);
      } else if (binding.getType() == Binding.SYSTEM) {
        sb.add(binding);
      } else {
        throw new UnsupportedOperationException("Unexpected binding type: " + binding.getType());
      }
    }
    this.userBindings = ub;
    this.systemBindings = Collections.unmodifiableList(sb);

    printBindings();

  }

  private void printBindings() {
    if (!DEBUG) {
      return;
    }
    System.out.println("SYSTEM");
    // printBindings(systemBindings);
    System.out.println("USER");
    // printBindings(userBindings);
  }

  private static final boolean JSON = true;


  // TODO(konigsberg): This is broken atm.
  @SuppressWarnings("unused")
  private static void printBindings(List<Binding> systemBindings) {
    StringBuilder output = new StringBuilder("[\n");
    for (Binding b : systemBindings) {

      CharSequence toPrint;
      if (JSON) {
        output.append(serializeToJson(b)).append(",\n");
      } else {
        toPrint = serializeToZ(b);
      }
    }
    output.append("]");
    deserialize(output);

    System.out.println(output);
  }

  @SuppressWarnings("unchecked")
  private static void deserialize(CharSequence json) {
    Object[] o = (Object[]) deSerializeFromJson(json);
    Map<String,Object> x = (Map<String,Object>) o[0];

    Map<String, Object> command = (Map<String,Object>)x.get("command");

    KeyBindingSpec binding = new KeyBindingSpec(
        command.get("id").toString(),
        x.get("keys").toString());

    Object temp = command.get("parameters");
    if (temp != null) {
      Map<String, String> params = (Map<String,String>)temp;
      for (String param : params.keySet()) {
        binding = binding.withParam(param, params.get(param));
      }
    }
  }

  private static CharSequence serializeToJson(Binding b) {
    boolean remove = b.getParameterizedCommand() == null;
    String platform = b.getPlatform();

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("action", remove ? "rem" : "add");
    map.put("platform", platform == null ? "" : platform);
    map.put("scheme", b.getSchemeId());
    map.put("context", b.getContextId());
    map.put("keys", b.getTriggerSequence().format());
    if (!remove) {
      Map<String,Object> commandMap = new LinkedHashMap<String, Object>();
      ParameterizedCommand parameterizedCommand = b.getParameterizedCommand();
      Command command = parameterizedCommand.getCommand();
      commandMap.put("id", command.getId());

      @SuppressWarnings("unchecked") // Eclipse doesn't support generics
      Map<String,String> parameters = parameterizedCommand.getParameterMap();
      if (parameters.size() > 0) {
        commandMap.put("parameters", parameters);
      }
      map.put("command", commandMap);
    }
    String json = new Gson().toJson(map);
    return json;
  }

  private static Object deSerializeFromJson(CharSequence json) {
    Object o = new Gson().fromJson(json.toString(), Object.class);
    return o;
  }

  private static CharSequence serializeToZ(Binding b) {
      String formattedCommand = formatCommand(b);
      boolean remove = false;
      if (formattedCommand.equals("")) {
        remove = true;
      }
      String platform = b.getPlatform();

      StringBuilder toPrint = new StringBuilder();
      toPrint.append(remove ? "-" : "+").append("{");
      toPrint.append(platform == null ? "" : platform).append(":");
      toPrint.append(b.getSchemeId()).append(":");
      toPrint.append(b.getContextId()).append(":");
      toPrint.append(b.getTriggerSequence().format()).append("}");
      if (!remove) {
        toPrint.append(":").append(formattedCommand);
      }
      return toPrint;
  }

  private static String formatCommand(Binding b) {
    if (JSON) {
      return formatCommandJSON(b);
    } else {
    return formatCommandZ(b);
    }
  }

  private static String formatCommandJSON(Binding b) {
    return null;
  }

  private static String formatCommandZ(Binding b) {

    ParameterizedCommand parameterizedCommand = b.getParameterizedCommand();
    if (parameterizedCommand == null) {
      return "";
    }
    Command command = parameterizedCommand.getCommand();
    StringBuilder sb = new StringBuilder("{" + command.getId());
    @SuppressWarnings("unchecked")
    Map<String,String> parameterMap = parameterizedCommand.getParameterMap();
    String prefix = "[";
    sb.append(prefix);
    if (parameterMap.size() > 0) {
      for (String key : parameterMap.keySet()) {
        sb.append(prefix);
        prefix = ",";
        sb.append(urlEncoded(key) + "=" + urlEncoded(parameterMap.get(key)));
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  private static String urlEncoded(String key) {
    try {
      return URLEncoder.encode(key, "US-ASCII");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Bind a scheme / trigger sequence to a command.
   *
   * @return {@code true} if the model has changed.
   */
  public boolean addIfNotPresent(
      Scheme scheme,
      String platform,
      String contextId,
      KeySequence triggerSequence,
      Command command,
      Map<String, String> params) {
    Binding binding = find(scheme, triggerSequence, command.getId(), params);
    // If no binding exists, create the user binding, add it and return true.
    if (binding == null) {
      addUserBinding(createBinding(scheme, platform, contextId, triggerSequence, command, params));
      return true;
    }

    /*
     * If a system binding exists for this scheme / sequence, find out if there's a
     * user binding hiding it, and if so remove it.
     */
    if ((binding.getType() == Binding.SYSTEM)) {
      // Finding a user binding to a null command.
      // ZORZELLA: do we even need to supply params?
      Binding userBinding = find(scheme, triggerSequence, null, params, userBindings);
      if (userBinding != null) {
        userBindings.remove(userBinding);
        return true;
      }
    }
    return false;
  }

  /**
   * Remove a binding.
   *
   * @return {@code true} if the model has changed.
   */
  public boolean removeBindingIfPresent(
      Scheme scheme,
      String platform,
      String contextId,
      KeySequence triggerSequence,
      Command command,
      Map<String, String> params) {

    Binding binding = find(scheme, triggerSequence, command.getId(), params, userBindings);

    if (binding != null) {
      userBindings.remove(binding);
      return true;
    }

    binding = find(scheme, triggerSequence, command.getId(), params, systemBindings);
    if (binding != null) {
      if (find(scheme, triggerSequence, null, params, userBindings) == null) {
        addUserBinding(createBinding(scheme, platform, contextId, triggerSequence, null, params));
        return true;
      }
    }
    return false;
  }

  private void addUserBinding(Binding binding) {
    if (binding.getType() != Binding.USER) {
      throw new IllegalArgumentException("Can only accept user bindings.");
    }
    userBindings.add(binding);
  }

  public Binding find(
      Scheme scheme,
      KeySequence triggerSequence,
      String cid,
      Map<String, String> params) {
    Binding userBinding = find(scheme, triggerSequence, cid, params, userBindings);
    if (userBinding != null) {
      return userBinding;
    }
    return find(scheme, triggerSequence, cid, params, systemBindings);
  }

  /**
   * Finds a binding in {@code list} that matches the given
   * {@code triggerSequence}, {@code scheme} and {@code cid}. If not found,
   * return {@code null}.
   */
  private Binding find(
      Scheme scheme,
      KeySequence triggerSequence,
      String cid,
      Map<String,String> params,
      List<Binding> list) {
    for (Binding binding : list) {
      if (binding.getSchemeId().equals(scheme.getId())
        && (binding.getTriggerSequence().equals(triggerSequence))) {
          ParameterizedCommand param = binding.getParameterizedCommand();
          if (param == null) {
            if (cid == null) {
              return binding;
            }
            continue;
          }
          Command command = param.getCommand();
          if (cid == null) {
            return command == null ? binding : null;
          }
          if (cid.equals(command.getId())) {

            @SuppressWarnings("unchecked") // Eclipse doesn't support generics.
            Map<String,String> temp = param.getParameterMap();
            if (equalMaps(temp, params)) {
              return binding;
            }
          }
        }
    }
    return null;
  }

  /**
   * compares first to second, where an empty map substitues for null.
   */
  private boolean equalMaps(
      Map<String,String> first,
      Map<String, String> second) {
    if (first == null) {
      first = Collections.emptyMap();
    }
    if (second == null) {
      second = Collections.emptyMap();
    }
    if (DEBUG) {
      for(Map.Entry<String,String> e : first.entrySet()) {
        System.out.println(e.getKey() + ":" + e.getValue());
        System.out.println(e.getKey().getClass() + ":" + e.getValue().getClass());
      }
    }
    return second.equals(first);
  }

  private Binding createBinding(
      Scheme scheme,
      String platform,
      String contextId,
      KeySequence triggerSequence,
      Command command,
      Map<String,String> params) {
    ParameterizedCommand parameterizedCommand;
    if (command == null) {
      parameterizedCommand = null;
    } else {
      parameterizedCommand = ParameterizedCommand.generateCommand(command, params);
    }

    Binding newBinding =
        new KeyBinding(triggerSequence, parameterizedCommand, scheme.getId(),
            contextId, null, platform, null, Binding.USER);

    return newBinding;
  }

  /**
   * Provides the full set of bindings as an array, to be supplied by the binding service.
   */
  public Binding[] toArray() {
    Binding[] result = new Binding[userBindings.size() + systemBindings.size()];
    for (int i = 0; i < userBindings.size(); i++) {
      result[i] = userBindings.get(i);
    }
    int offset = userBindings.size();
    for (int i = 0; i < systemBindings.size(); i++) {
      result[i + offset] = systemBindings.get(i);
    }
    return result;
  }
}