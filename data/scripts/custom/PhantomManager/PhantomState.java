package custom.PhantomManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.l2jmobius.gameserver.model.Location;

public class PhantomState
{
	public static final Map<Integer, List<Location>> NPC_ANCHORS = new ConcurrentHashMap<>();
	public static final Map<Integer, Boolean> MP_RECOVERY_STATE = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> GEAR_GRADE = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> GEAR_PACK = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> STUCK_COUNTERS = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> EMPTY_TARGET_COUNTERS = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> HUNT_LEVEL_BAND = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> GOAL_LEVEL = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> NEXT_HUNT_TELEPORT = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> LAST_AI_TRACE = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> LAST_ENGINE_TRACE = new ConcurrentHashMap<>(); // Throttle propio del tick del engine: si compartiera clave con LAST_AI_TRACE, el "tick online=..." (que corre siempre primero) consumiria la ventana y las trazas de decision no se escribirian nunca.
	public static final Map<Integer, Long> SWEEP_LAST_TRY = new ConcurrentHashMap<>(); // Momento del ultimo intento de sweep: limita el ritmo a un intento cada 4s cuando el cast falla (sobrepeso, cadaver viejo), sin ahogar la recogida de drops.
	public static final Map<Integer, Long> CITY_REST_UNTIL = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> LAST_SELF_BUFF = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> LAST_SKILL_LEARN_LEVEL = new ConcurrentHashMap<>();
	public static final Map<Integer, Location> LAST_TICK_POSITION = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> COMBAT_STILL_SINCE = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> IGNORED_MOB = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> IGNORED_MOB_UNTIL = new ConcurrentHashMap<>();
	public static final Map<Integer, Boolean> HP_RECOVERY_STATE = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> LAST_POTION = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> LAST_SUMMON_TRY = new ConcurrentHashMap<>();
	public static final Map<Integer, Integer> LAST_TICK_HP = new ConcurrentHashMap<>();
	public static final Map<Integer, Long> SESSION_END = new ConcurrentHashMap<>();
	public static final Set<Integer> AGGRESSIVE_PHANTOMS = ConcurrentHashMap.newKeySet();
	public static final Set<Integer> PK_PHANTOMS = ConcurrentHashMap.newKeySet();
	public static final Set<Integer> REVIVING_PHANTOMS = ConcurrentHashMap.newKeySet();
	
	public static void register(int objectId, boolean aggressive, boolean pkMode)
	{
		MP_RECOVERY_STATE.put(objectId, false);
		GEAR_GRADE.put(objectId, -1);
		GEAR_PACK.put(objectId, -1);
		STUCK_COUNTERS.put(objectId, 0);
		EMPTY_TARGET_COUNTERS.put(objectId, 0);
		HUNT_LEVEL_BAND.put(objectId, -1);
		GOAL_LEVEL.put(objectId, 0);
		NEXT_HUNT_TELEPORT.put(objectId, 0L);
		LAST_AI_TRACE.put(objectId, 0L);
		CITY_REST_UNTIL.put(objectId, 0L);
		LAST_SELF_BUFF.put(objectId, 0L);
		LAST_SKILL_LEARN_LEVEL.put(objectId, 0);
		if (aggressive)
		{
			AGGRESSIVE_PHANTOMS.add(objectId);
		}
		if (pkMode)
		{
			PK_PHANTOMS.add(objectId);
		}
	}
	
	public static void unregister(int objectId)
	{
		MP_RECOVERY_STATE.remove(objectId);
		GEAR_GRADE.remove(objectId);
		GEAR_PACK.remove(objectId);
		STUCK_COUNTERS.remove(objectId);
		EMPTY_TARGET_COUNTERS.remove(objectId);
		HUNT_LEVEL_BAND.remove(objectId);
		GOAL_LEVEL.remove(objectId);
		NEXT_HUNT_TELEPORT.remove(objectId);
		LAST_AI_TRACE.remove(objectId);
		LAST_ENGINE_TRACE.remove(objectId);
		SWEEP_LAST_TRY.remove(objectId);
		CITY_REST_UNTIL.remove(objectId);
		LAST_SELF_BUFF.remove(objectId);
		LAST_SKILL_LEARN_LEVEL.remove(objectId);
		LAST_TICK_POSITION.remove(objectId);
		COMBAT_STILL_SINCE.remove(objectId);
		IGNORED_MOB.remove(objectId);
		IGNORED_MOB_UNTIL.remove(objectId);
		HP_RECOVERY_STATE.remove(objectId);
		LAST_POTION.remove(objectId);
		LAST_SUMMON_TRY.remove(objectId);
		LAST_TICK_HP.remove(objectId);
		SESSION_END.remove(objectId);
		AGGRESSIVE_PHANTOMS.remove(objectId);
		PK_PHANTOMS.remove(objectId);
		REVIVING_PHANTOMS.remove(objectId);
	}
	
	public static void clear()
	{
		MP_RECOVERY_STATE.clear();
		GEAR_GRADE.clear();
		GEAR_PACK.clear();
		STUCK_COUNTERS.clear();
		EMPTY_TARGET_COUNTERS.clear();
		HUNT_LEVEL_BAND.clear();
		GOAL_LEVEL.clear();
		NEXT_HUNT_TELEPORT.clear();
		LAST_AI_TRACE.clear();
		LAST_ENGINE_TRACE.clear();
		SWEEP_LAST_TRY.clear();
		CITY_REST_UNTIL.clear();
		LAST_SELF_BUFF.clear();
		LAST_SKILL_LEARN_LEVEL.clear();
		LAST_TICK_POSITION.clear();
		COMBAT_STILL_SINCE.clear();
		IGNORED_MOB.clear();
		IGNORED_MOB_UNTIL.clear();
		HP_RECOVERY_STATE.clear();
		LAST_POTION.clear();
		LAST_SUMMON_TRY.clear();
		LAST_TICK_HP.clear();
		SESSION_END.clear();
		NPC_ANCHORS.clear();
		AGGRESSIVE_PHANTOMS.clear();
		PK_PHANTOMS.clear();
		REVIVING_PHANTOMS.clear();
	}
	
	public static List<Location> getAnchors(int level)
	{
		// CopyOnWrite: N ticks de phantoms escriben anclajes a la vez; un ArrayList plano se corrompe.
		return NPC_ANCHORS.computeIfAbsent(level, k -> new CopyOnWriteArrayList<>());
	}
	
	public static boolean isAggressive(int objectId)
	{
		return AGGRESSIVE_PHANTOMS.contains(objectId);
	}
	
	public static boolean isPk(int objectId)
	{
		return PK_PHANTOMS.contains(objectId);
	}
}
