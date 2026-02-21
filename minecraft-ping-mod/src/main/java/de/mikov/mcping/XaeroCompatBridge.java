package de.mikov.mcping;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class XaeroCompatBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcping-xaero-compat");
    private static final String PING_NAME = "Ping";
    private static final String PING_WARNING_NAME = "Ping Warning";
    private static final String PING_GO_NAME = "Ping Go";

    private static final Map<String, Object> ACTIVE_WAYPOINTS = new ConcurrentHashMap<>();

    private static volatile boolean initialized;
    private static volatile boolean xaeroPresent;

    private static volatile Method waypointSetAddMethod;
    private static volatile Method waypointSetRemoveMethod;
    private static volatile Constructor<?> waypointCtor;
    private static volatile Method waypointColorValueOfMethod;
    private static volatile Method waypointSetChangedMethod;
    private static volatile Method waypointSetTemporaryMethod;
    private static volatile Method waypointVisibilityValueOfMethod;
    private static volatile Method waypointSetVisibilityMethod;
    private static volatile Method waypointGetNameMethod;
    private static volatile Method waypointGetXMethod;
    private static volatile Method waypointGetYMethod;
    private static volatile Method waypointGetZMethod;
    private static volatile Method waypointSetGetWaypointsMethod;

    private XaeroCompatBridge() {
    }

    public static boolean isOwnPingWaypoint(Object waypoint) {
        if (waypoint == null) {
            return false;
        }

        if (ACTIVE_WAYPOINTS.containsValue(waypoint)) {
            return true;
        }

        if (waypointGetNameMethod == null) {
            return false;
        }

        try {
            String name = String.valueOf(waypointGetNameMethod.invoke(waypoint));
            return PING_NAME.equals(name) || PING_WARNING_NAME.equals(name) || PING_GO_NAME.equals(name);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    public static void upsertPing(PingRecord ping) {
        if (ping == null || !ensureXaeroPresent()) {
            return;
        }

        WaypointContext context = resolveWaypointContext();
        if (context == null || !ensureHooksResolved(context)) {
            return;
        }

        String key = pingKey(ping);
        Object existingWaypoint = ACTIVE_WAYPOINTS.remove(key);
        if (existingWaypoint != null) {
            removeWaypoint(context, existingWaypoint);
        }

        Object newWaypoint = createWaypoint(ping);
        if (newWaypoint == null) {
            return;
        }

        if (addWaypoint(context, newWaypoint)) {
            ACTIVE_WAYPOINTS.put(key, newWaypoint);
        }
    }

    public static void removePing(PingRecord ping) {
        if (ping == null || !ensureXaeroPresent()) {
            return;
        }

        WaypointContext context = resolveWaypointContext();
        if (context == null || !ensureHooksResolved(context)) {
            return;
        }

        String key = pingKey(ping);
        Object waypoint = ACTIVE_WAYPOINTS.remove(key);
        if (waypoint != null) {
            removeWaypoint(context, waypoint);
        }
    }

    private static boolean ensureXaeroPresent() {
        if (initialized) {
            return xaeroPresent;
        }

        synchronized (XaeroCompatBridge.class) {
            if (initialized) {
                return xaeroPresent;
            }

            xaeroPresent = FabricLoader.getInstance().isModLoaded("xaerominimap")
                    || FabricLoader.getInstance().isModLoaded("xaeroworldmap");
            initialized = true;

            if (xaeroPresent) {
                LOGGER.info("Xaero mod detected. Enabling ping waypoint compatibility.");
            }

            return xaeroPresent;
        }
    }

    private static WaypointContext resolveWaypointContext() {
        try {
            Object xaeroSession = invokeStaticNoArg("xaero.common.XaeroMinimapSession", "getCurrentSession");
            if (xaeroSession == null) {
                return null;
            }

            Object minimapProcessor = invokeNoArg(xaeroSession, "getMinimapProcessor");
            if (minimapProcessor == null) {
                return null;
            }

            Object minimapSession = invokeNoArg(minimapProcessor, "getSession");
            if (minimapSession == null) {
                return null;
            }

            Object worldManager = invokeNoArg(minimapSession, "getWorldManager");
            if (worldManager == null) {
                return null;
            }

            Object currentWorld = invokeNoArg(worldManager, "getCurrentWorld");
            if (currentWorld == null) {
                return null;
            }

            Object waypointSet = invokeNoArg(currentWorld, "getCurrentWaypointSet");
            if (waypointSet == null) {
                return null;
            }

            Object waypointSession = invokeNoArg(minimapSession, "getWaypointSession");
            return new WaypointContext(waypointSet, waypointSession);
        } catch (Throwable ex) {
            LOGGER.debug("Failed to resolve Xaero waypoint context: {}", ex.getMessage());
            return null;
        }
    }

    private static boolean ensureHooksResolved(WaypointContext context) {
        if (waypointSetAddMethod != null && waypointSetRemoveMethod != null && waypointCtor != null && waypointColorValueOfMethod != null) {
            return true;
        }

        synchronized (XaeroCompatBridge.class) {
            if (waypointSetAddMethod != null && waypointSetRemoveMethod != null && waypointCtor != null && waypointColorValueOfMethod != null) {
                return true;
            }

            try {
                Class<?> waypointSetClass = context.waypointSet().getClass();
                Class<?> waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
                Class<?> waypointColorClass = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");

                for (Method method : waypointSetClass.getMethods()) {
                    if (method.getParameterCount() != 1) {
                        continue;
                    }

                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (!parameterType.getName().equals(waypointClass.getName())) {
                        continue;
                    }

                    String methodName = method.getName();
                    if ("add".equals(methodName)) {
                        waypointSetAddMethod = method;
                    } else if ("remove".equals(methodName)) {
                        waypointSetRemoveMethod = method;
                    }
                }

                waypointCtor = waypointClass.getConstructor(int.class, int.class, int.class, String.class, String.class, waypointColorClass);
                waypointColorValueOfMethod = waypointColorClass.getMethod("valueOf", String.class);
                Class<?> visibilityClass = Class.forName("xaero.hud.minimap.waypoint.WaypointVisibilityType");
                waypointVisibilityValueOfMethod = visibilityClass.getMethod("valueOf", String.class);

                try {
                    waypointSetVisibilityMethod = waypointClass.getMethod("setVisibility", visibilityClass);
                } catch (NoSuchMethodException ignored) {
                    waypointSetVisibilityMethod = null;
                }

                try {
                    waypointSetTemporaryMethod = waypointClass.getMethod("setTemporary", boolean.class);
                } catch (NoSuchMethodException ignored) {
                    waypointSetTemporaryMethod = null;
                }

                waypointGetNameMethod = waypointClass.getMethod("getName");
                waypointGetXMethod = waypointClass.getMethod("getX");
                waypointGetYMethod = waypointClass.getMethod("getY");
                waypointGetZMethod = waypointClass.getMethod("getZ");
                waypointSetGetWaypointsMethod = waypointSetClass.getMethod("getWaypoints");

                if (context.waypointSession() != null) {
                    try {
                        waypointSetChangedMethod = context.waypointSession().getClass().getMethod("setSetChangedTime", long.class);
                    } catch (NoSuchMethodException ignored) {
                        waypointSetChangedMethod = null;
                    }
                }

                if (waypointSetAddMethod == null || waypointSetRemoveMethod == null) {
                    LOGGER.warn("Xaero waypoint hooks unresolved: add/remove methods not found on {}", waypointSetClass.getName());
                    return false;
                }

                return true;
            } catch (ClassNotFoundException | NoSuchMethodException ex) {
                LOGGER.warn("Xaero waypoint class resolution failed: {}", ex.getMessage());
                return false;
            }
        }
    }

    private static Object createWaypoint(PingRecord ping) {
        try {
            int x = (int) Math.round(ping.position().x);
            int y = (int) Math.round(ping.position().y);
            int z = (int) Math.round(ping.position().z);

            String name = switch (ping.type()) {
                case WARNING -> "Ping Warning";
                case GO -> "Ping Go";
                case NORMAL -> "Ping";
            };
            String initials = switch (ping.type()) {
                case WARNING -> "!";
                case GO -> "G";
                case NORMAL -> "P";
            };
            String colorName = switch (ping.type()) {
                case WARNING -> "YELLOW";
                case GO -> "GREEN";
                case NORMAL -> "BLUE";
            };

            Object color = waypointColorValueOfMethod.invoke(null, colorName);
            Object waypoint = waypointCtor.newInstance(x, y, z, name, initials, color);

            if (waypointSetTemporaryMethod != null) {
                waypointSetTemporaryMethod.invoke(waypoint, true);
            }

            if (waypointSetVisibilityMethod != null && waypointVisibilityValueOfMethod != null) {
                Object localVisibility = waypointVisibilityValueOfMethod.invoke(null, "LOCAL");
                waypointSetVisibilityMethod.invoke(waypoint, localVisibility);
            }

            return waypoint;
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException ex) {
            LOGGER.debug("Failed to create Xaero waypoint: {}", ex.getMessage());
            return null;
        }
    }

    private static boolean addWaypoint(WaypointContext context, Object waypoint) {
        try {
            waypointSetAddMethod.invoke(context.waypointSet(), waypoint);
            removeOverlappingDefaultWaypoints(context, waypoint);
            touchSetChanged(context);
            return true;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.debug("Failed to add Xaero waypoint: {}", ex.getMessage());
            return false;
        }
    }

    private static void removeOverlappingDefaultWaypoints(WaypointContext context, Object ownWaypoint) {
        if (waypointSetGetWaypointsMethod == null || waypointGetNameMethod == null || waypointGetXMethod == null || waypointGetYMethod == null || waypointGetZMethod == null) {
            return;
        }

        try {
            Object waypointsObj = waypointSetGetWaypointsMethod.invoke(context.waypointSet());
            if (!(waypointsObj instanceof Iterable<?> iterable)) {
                return;
            }

            List<Object> toRemove = new ArrayList<>();
            for (Object waypoint : iterable) {
                if (waypoint == null || waypoint == ownWaypoint) {
                    continue;
                }

                String waypointName = String.valueOf(waypointGetNameMethod.invoke(waypoint));
                if ("default".equalsIgnoreCase(waypointName)) {
                    toRemove.add(waypoint);
                }
            }

            for (Object waypoint : toRemove) {
                waypointSetRemoveMethod.invoke(context.waypointSet(), waypoint);
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private static void removeWaypoint(WaypointContext context, Object waypoint) {
        try {
            waypointSetRemoveMethod.invoke(context.waypointSet(), waypoint);
            touchSetChanged(context);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.debug("Failed to remove Xaero waypoint: {}", ex.getMessage());
        }
    }

    private static void touchSetChanged(WaypointContext context) {
        if (context.waypointSession() == null || waypointSetChangedMethod == null) {
            return;
        }

        try {
            waypointSetChangedMethod.invoke(context.waypointSession(), System.currentTimeMillis());
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static Object invokeStaticNoArg(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName);
            return method.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static String pingKey(PingRecord ping) {
        return normalize(ping.sender())
                + "|" + normalize(ping.serverId())
                + "|" + normalize(ping.dimension())
                + "|" + ping.type().wireValue()
                + "|" + Math.round(ping.position().x)
                + ":" + Math.round(ping.position().y)
                + ":" + Math.round(ping.position().z)
                + "|" + ping.expiresAtMs();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record WaypointContext(Object waypointSet, Object waypointSession) {
    }
}
