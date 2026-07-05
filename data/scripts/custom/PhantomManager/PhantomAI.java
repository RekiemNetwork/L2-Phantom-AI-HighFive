package custom.PhantomManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.util.LocationUtil;

public class PhantomAI
{
	private static final int MP_REST_PERCENT = 10;
	private static final int MP_READY_PERCENT = 100;
	private static final int EMPTY_TARGET_RELOCATE_LIMIT = 3;
	private static final int GOAL_LEVEL_STEP_MIN = 4;
	private static final int GOAL_LEVEL_STEP_MAX = 6;
	private static final int CITY_REST_MIN_TIME = 45000;
	private static final int CITY_REST_MAX_TIME = 90000;
	
	public static void thinkAndFarm(Player bot)
	{
		tryDrinkPotion(bot); // No bloquea: los jugadores beben pociones tambien en pleno combate.

		if (handleCriticalHp(bot))
		{
			return;
		}

		if (handleBlockedCombat(bot))
		{
			return;
		}

		if (handleLifeGoal(bot))
		{
			return;
		}
		
		if (handleMageMpRecovery(bot))
		{
			trace(bot, "modo reposo/escape por MP");
			return;
		}
		
		if (handleHpRecovery(bot))
		{
			trace(bot, "reposo de HP");
			return;
		}

		int stuckCount = PhantomState.STUCK_COUNTERS.getOrDefault(bot.getObjectId(), 0);
		
		if (stuckCount >= 2)
		{
			trace(bot, "atascado, relocalizando");
			relocateStuckBot(bot);
			return;
		}
		
		if (bot.isCastingNow() || bot.isAttackingNow() || bot.isMoving())
		{
			trace(bot, "ocupado: moving=" + bot.isMoving() + " casting=" + bot.isCastingNow() + " attacking=" + bot.isAttackingNow());
			return;
		}
		
		if (PhantomHuntingSpots.relocateForLevelIfNeeded(bot))
		{
			trace(bot, "teleport por rango de level");
			return;
		}
		
		tryCasualChat(bot);
		
		Player aggressor = findPvPAttacker(bot);
		if (aggressor != null)
		{
			trace(bot, "defendiendo contra " + aggressor.getName());
			bot.setTarget(aggressor);
			executeAttackPlan(bot, aggressor);
			return;
		}
		
		if (pickupNearbyDrops(bot))
		{
			trace(bot, "recogiendo drops");
			return;
		}

		Player victim = findAggressiveTarget(bot);
		if (victim != null)
		{
			trace(bot, "objetivo PvP/PK " + victim.getName());
			makePkIfNeeded(bot, victim);
			bot.setTarget(victim);
			executeAttackPlan(bot, victim);
			return;
		}
		
		Monster target = findFarmTarget(bot);
		if (target != null)
		{
			PhantomState.EMPTY_TARGET_COUNTERS.put(bot.getObjectId(), 0);
			PhantomManager.logToFile(bot.getName(), "Atacando a " + target.getName());
			bot.setTarget(target);
			executeAttackPlan(bot, target);
			return;
		}
		
		handleNoFarmTarget(bot);
	}
	
	private static boolean handleLifeGoal(Player bot)
	{
		int objectId = bot.getObjectId();
		long now = System.currentTimeMillis();
		long restUntil = PhantomState.CITY_REST_UNTIL.getOrDefault(objectId, 0L);
		
		if (restUntil > now)
		{
			recoverInTown(bot);
			trace(bot, "descansando en ciudad");
			return true;
		}
		
		if (restUntil != 0L)
		{
			PhantomState.CITY_REST_UNTIL.put(objectId, 0L);
			assignNewGoal(bot);
			if (bot.isSitting())
			{
				bot.standUp();
			}
			PhantomHuntingSpots.travelToLevelSpot(bot, "Descanso terminado. Vuelve a farmear");
			trace(bot, "sale de ciudad hacia nuevo spot");
			return true;
		}
		
		int goalLevel = PhantomState.GOAL_LEVEL.getOrDefault(objectId, 0);
		if (goalLevel <= bot.getLevel())
		{
			if (goalLevel <= 0)
			{
				assignNewGoal(bot);
				return false;
			}
			
			travelToNearestTown(bot, "Meta cumplida L" + bot.getLevel() + "/" + goalLevel);
			return true;
		}
		return false;
	}
	
	private static void assignNewGoal(Player bot)
	{
		int goal = Math.min(99, bot.getLevel() + Rnd.get(GOAL_LEVEL_STEP_MIN, GOAL_LEVEL_STEP_MAX));
		PhantomState.GOAL_LEVEL.put(bot.getObjectId(), goal);
		PhantomManager.logToFile(bot.getName(), "Nueva meta: subir hasta nivel " + goal);
	}
	
	private static void travelToNearestTown(Player bot, String reason)
	{
		if (bot.getKarma() > 0)
		{
			// Un PK no puede descansar en ciudad (guardias): descansa donde esta.
			PhantomState.CITY_REST_UNTIL.put(bot.getObjectId(), System.currentTimeMillis() + Rnd.get(CITY_REST_MIN_TIME, CITY_REST_MAX_TIME));
			PhantomManager.logToFile(bot.getName(), reason + ". PK: descansa fuera de ciudad.");
			return;
		}
		Player observer = PhantomEngine.getNearestRealObserver(bot, 2000);
		if (observer != null)
		{
			PhantomEngine.walkAwayFrom(bot, observer);
			trace(bot, "viaje a ciudad pospuesto: se aleja andando de un jugador real");
			return;
		}
		PhantomConfig.FarmZone nearest = null;
		long bestDistance = Long.MAX_VALUE;
		for (PhantomConfig.FarmZone zone : PhantomConfig.ROUTES)
		{
			long dx = bot.getX() - zone.townX;
			long dy = bot.getY() - zone.townY;
			long distance = (dx * dx) + (dy * dy);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				nearest = zone;
			}
		}
		
		if (nearest == null)
		{
			return;
		}
		
		Location safeTown = PhantomGeo.getSafeSpawn(new Location(nearest.townX, nearest.townY, nearest.townZ), 150);
		PhantomEngine.movePhantomTo(bot, safeTown, reason + ". Viaja a ciudad cercana");
		PhantomState.HUNT_LEVEL_BAND.put(bot.getObjectId(), -1);
		PhantomState.CITY_REST_UNTIL.put(bot.getObjectId(), System.currentTimeMillis() + Rnd.get(CITY_REST_MIN_TIME, CITY_REST_MAX_TIME));
		PhantomManager.logToFile(bot.getName(), reason + ". Descansa en ciudad hasta recuperar recursos.");
	}
	
	private static void recoverInTown(Player bot)
	{
		if (bot.isDead())
		{
			return;
		}
		if (bot.isMoving())
		{
			return;
		}
		if (!bot.isSitting())
		{
			bot.getAI().setIntention(Intention.REST);
			bot.sitDown();
		}
		
		double mpGain = Math.max(1, bot.getMaxMp() * 0.08);
		double hpGain = Math.max(1, bot.getMaxHp() * 0.04);
		bot.setCurrentMp(Math.min(bot.getMaxMp(), bot.getCurrentMp() + mpGain));
		bot.setCurrentHp(Math.min(bot.getMaxHp(), bot.getCurrentHp() + hpGain));
		bot.broadcastUserInfo();
	}
	
	private static boolean handleMageMpRecovery(Player bot)
	{
		if (!bot.isMageClass())
		{
			return false;
		}
		
		boolean recovering = PhantomState.MP_RECOVERY_STATE.getOrDefault(bot.getObjectId(), false);
		if (!recovering && (bot.getCurrentMpPercent() <= MP_REST_PERCENT))
		{
			recovering = true;
			PhantomState.MP_RECOVERY_STATE.put(bot.getObjectId(), true);
			PhantomManager.logToFile(bot.getName(), "Sin MP. Entrando en modo reposo.");
		}
		
		if (!recovering)
		{
			return false;
		}
		
		if (bot.getCurrentMpPercent() >= MP_READY_PERCENT)
		{
			PhantomState.MP_RECOVERY_STATE.put(bot.getObjectId(), false);
			if (bot.isSitting())
			{
				bot.standUp();
			}
			PhantomManager.logToFile(bot.getName(), "MP completo. Vuelve al combate.");
			return false;
		}
		
		Player danger = findPvPAttacker(bot);
		if (danger != null)
		{
			if (bot.isSitting())
			{
				bot.standUp();
			}
			runAwayFrom(bot, danger);
			return true;
		}
		
		if (!bot.isSitting() && !bot.isMoving())
		{
			bot.getAI().setIntention(Intention.REST);
			bot.sitDown();
		}
		return true;
	}
	
	private static void relocateStuckBot(Player bot)
	{
		Player observer = PhantomEngine.getNearestRealObserver(bot, 2000);
		if (observer != null)
		{
			PhantomEngine.walkAwayFrom(bot, observer);
			trace(bot, "teleport pospuesto: se aleja andando de un jugador real");
			return;
		}
		Location anchor = getSafeAnchor(bot);
		for (int i = 0; (anchor != null) && (i < 5) && (PhantomEngine.countPhantomsNear(anchor, 1200) >= 3); i++)
		{
			anchor = getSafeAnchor(bot);
		}
		if (anchor != null)
		{
			Location safe = PhantomGeo.getSafeSpawn(anchor, 250);
			PhantomManager.logToFile(bot.getName(), "Viajando a anclaje de NPC: " + safe.getX() + ", " + safe.getY() + ", " + safe.getZ());
			PhantomEngine.movePhantomTo(bot, safe, "Viajando a anclaje de NPC");
		}
		else
		{
			PhantomConfig.FarmZone zone = PhantomConfig.getIdealZone(bot.getLevel());
			int safeZ = GeoEngine.getInstance().getHeight(zone.spotX, zone.spotY, zone.spotZ);
			PhantomManager.logToFile(bot.getName(), "Calculando Z con GeoEngine: " + safeZ);
			PhantomEngine.movePhantomTo(bot, new Location(zone.spotX + Rnd.get(-100, 100), zone.spotY + Rnd.get(-100, 100), safeZ), "Viajando a zona fallback");
		}
		bot.broadcastUserInfo();
		PhantomState.STUCK_COUNTERS.put(bot.getObjectId(), 0);
	}
	
	private static Location getSafeAnchor(Player bot)
	{
		// Los anclajes son una lista GLOBAL (los llenan todos los phantoms): elegir al azar teleportaba a un elfo oscuro al anclaje de un humano en Talking Island. Solo los mas cercanos.
		List<Location> anchors = new ArrayList<>();
		for (int i = bot.getLevel() - 3; i <= (bot.getLevel() + 3); i++)
		{
			List<Location> levelAnchors = PhantomState.NPC_ANCHORS.get(i);
			if (levelAnchors != null)
			{
				anchors.addAll(levelAnchors);
			}
		}
		if (anchors.isEmpty())
		{
			return null;
		}
		anchors.sort(Comparator.comparingDouble(bot::calculateDistance2D));
		List<Location> nearest = anchors.subList(0, Math.min(8, anchors.size()));
		return nearest.get(Rnd.get(nearest.size()));
	}
	
	private static void tryCasualChat(Player bot)
	{
		if (!PhantomConfig.CHAT_ENABLED)
		{
			return;
		}
		if (!PhantomChat.isAiEnabled())
		{
			return; // Sin IA que responda, hablar a un jugador y luego ignorar sus respuestas delata; callar es lo humano.
		}
		if (Rnd.get(100) < 1)
		{
			Player nearby = World.getInstance().getVisibleObjectsInRange(bot, Player.class, 1000).stream().filter(p -> !p.isDead() && !p.isGM() && !PhantomEngine.activePhantoms.contains(p)).findFirst().orElse(null);
			if (nearby != null)
			{
				PhantomChat.requestGemini(bot, nearby, "Dile algo casual y amigable a un jugador llamado " + nearby.getName() + " que paso por tu lado.", false);
			}
		}
	}
	
	private static Player findPvPAttacker(Player bot)
	{
		for (Player p : World.getInstance().getVisibleObjectsInRange(bot, Player.class, 1500))
		{
			if ((p == bot) || p.isDead() || p.isGM() || (p.getTarget() != bot))
			{
				continue;
			}
			
			if (PhantomEngine.activePhantoms.contains(p))
			{
				return p;
			}
			else if (p.getPvpFlag() > 0)
			{
				return p;
			}
		}
		return null;
	}
	
	private static Player findAggressiveTarget(Player bot)
	{
		if (!PhantomState.isAggressive(bot.getObjectId()) || (Rnd.get(100) >= 6))
		{
			return null;
		}
		
		boolean pkMode = PhantomState.isPk(bot.getObjectId());
		Player closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (Player p : World.getInstance().getVisibleObjectsInRange(bot, Player.class, 1200))
		{
			if (p.isDead() || p.isGM() || (p == bot))
			{
				continue;
			}
			if (!PhantomEngine.activePhantoms.contains(p))
			{
				continue; // El PvP/PK de los phantoms es teatro interno: jamas inician contra un jugador real.
			}
			if (!pkMode && (Rnd.get(100) >= 35))
			{
				continue;
			}
			
			double distance = LocationUtil.calculateDistance(bot, p, true, false);
			if (distance < closestDistance)
			{
				closest = p;
				closestDistance = distance;
			}
		}
		return closest;
	}
	
	private static void makePkIfNeeded(Player bot, Player victim)
	{
		if (!PhantomConfig.PK_ENABLED || !PhantomState.isPk(bot.getObjectId()) || (bot.getLevel() < PhantomConfig.PK_MIN_LEVEL) || (victim.getPvpFlag() > 0))
		{
			if (PhantomEngine.activePhantoms.contains(victim))
			{
				bot.startPvPFlag();
				victim.startPvPFlag();
			}
			return;
		}
		bot.setPkKills(Math.max(1, bot.getPkKills()));
		bot.setKarma(Rnd.get(360, 3600));
		bot.updatePvpTitleAndColor(true);
		bot.broadcastKarma();
		bot.broadcastUserInfo();
		PhantomManager.logToFile(bot.getName(), "Modo PK activado contra " + victim.getName());
	}
	
	private static void tryDrinkPotion(Player bot)
	{
		if (bot.isDead() || bot.isSitting() || (bot.getCurrentHpPercent() > 55))
		{
			return;
		}
		final long now = System.currentTimeMillis();
		if ((now - PhantomState.LAST_POTION.getOrDefault(bot.getObjectId(), 0L)) < 15000)
		{
			return;
		}
		final Item potion = bot.getInventory().getItemByItemId(PhantomConfig.potionForLevel(bot.getLevel()));
		if (potion == null)
		{
			return;
		}
		// Via el ItemHandler real: aplica la skill de pocion con su animacion visible (no un heal silencioso).
		final IItemHandler handler = ItemHandler.getInstance().getHandler(potion.getEtcItem());
		if (handler != null)
		{
			handler.onItemUse(bot, potion, false);
			PhantomState.LAST_POTION.put(bot.getObjectId(), now);
			PhantomManager.logToFile(bot.getName(), "Bebe pocion de vida (" + (int) bot.getCurrentHpPercent() + "% HP).");
		}
	}

	private static boolean handleCriticalHp(Player bot)
	{
		if (bot.isDead() || (bot.getCurrentHpPercent() > 15))
		{
			return false;
		}

		final Monster danger = findMonsterAttacker(bot);
		if (danger == null)
		{
			return false; // Sin mobs encima: el reposo de HP se encarga.
		}

		// Un jugador real no pelea hasta morir: suelta el objetivo y corre.
		bot.abortAttack();
		bot.abortCast();
		bot.setTarget(null);
		runAwayFrom(bot, danger);
		PhantomManager.logToFile(bot.getName(), "HP critico (" + (int) bot.getCurrentHpPercent() + "%). Huye de " + danger.getName());
		return true;
	}

	private static boolean handleHpRecovery(Player bot)
	{
		final int objectId = bot.getObjectId();
		final int currentHp = (int) bot.getCurrentHp();
		final int previousHp = PhantomState.LAST_TICK_HP.getOrDefault(objectId, currentHp);
		PhantomState.LAST_TICK_HP.put(objectId, currentHp);
		// El getTarget() del mob parpadea entre swings; la perdida de HP entre ticks no engana.
		final boolean losingHp = currentHp < previousHp;

		boolean recovering = PhantomState.HP_RECOVERY_STATE.getOrDefault(objectId, false);
		if (!recovering)
		{
			if ((bot.getCurrentHpPercent() > 40) || losingHp || bot.isAttackingNow() || bot.isCastingNow() || (findMonsterAttacker(bot) != null))
			{
				return false;
			}
			recovering = true;
			PhantomState.HP_RECOVERY_STATE.put(objectId, true);
			bot.setTarget(null);
			PhantomManager.logToFile(bot.getName(), "HP bajo sin peligro. Se sienta a recuperar.");
		}

		if (bot.getCurrentHpPercent() >= 90)
		{
			PhantomState.HP_RECOVERY_STATE.put(objectId, false);
			if (bot.isSitting())
			{
				bot.standUp();
			}
			PhantomManager.logToFile(bot.getName(), "HP recuperado. Vuelve al combate.");
			return false;
		}

		final Player pvpDanger = findPvPAttacker(bot);
		final Monster mobDanger = findMonsterAttacker(bot);
		if ((pvpDanger != null) || (mobDanger != null) || losingHp)
		{
			// Atacado mientras descansa: decision INMEDIATA (defenderse o huir) — re-sentarse creaba el bucle golpe-sienta-golpe hasta morir.
			PhantomState.HP_RECOVERY_STATE.put(objectId, false);
			if (bot.isSitting())
			{
				bot.standUp();
			}
			final Creature danger = (mobDanger != null) ? mobDanger : pvpDanger;
			if (danger == null)
			{
				return false; // Dano sin origen visible (DOT): en pie y que siga la IA normal.
			}
			if (bot.getCurrentHpPercent() <= 25)
			{
				runAwayFrom(bot, danger);
				PhantomManager.logToFile(bot.getName(), "Atacado descansando con poca vida. Huye de " + danger.getName());
			}
			else
			{
				bot.setTarget(danger);
				executeAttackPlan(bot, danger);
				PhantomManager.logToFile(bot.getName(), "Atacado descansando. Se defiende de " + danger.getName());
			}
			return true;
		}

		if (!bot.isSitting() && !bot.isMoving())
		{
			bot.getAI().setIntention(Intention.REST);
			bot.sitDown();
		}
		return true;
	}

	private static Monster findMonsterAttacker(Player bot)
	{
		Monster nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Monster m : World.getInstance().getVisibleObjectsInRange(bot, Monster.class, 1400))
		{
			if (!m.isDead() && (m.getTarget() == bot))
			{
				final double distance = bot.calculateDistance2D(m);
				if (distance < nearestDistance)
				{
					nearestDistance = distance;
					nearest = m;
				}
			}
		}
		return nearest;
	}

	private static boolean handleBlockedCombat(Player bot)
	{
		if ((bot.getAI().getIntention() != Intention.ATTACK) || !(bot.getTarget() instanceof Monster))
		{
			PhantomState.COMBAT_STILL_SINCE.remove(bot.getObjectId());
			return false;
		}

		Monster target = (Monster) bot.getTarget();
		Location last = PhantomState.LAST_TICK_POSITION.get(bot.getObjectId());
		PhantomState.LAST_TICK_POSITION.put(bot.getObjectId(), new Location(bot.getX(), bot.getY(), bot.getZ()));
		int range = Math.max(80, bot.getPhysicalAttackRange());
		boolean still = (last != null) && (bot.calculateDistance2D(last) < 30);
		boolean outOfReach = !target.isDead() && (bot.calculateDistance2D(target) > (range + 60));
		if (!still || !outOfReach)
		{
			PhantomState.COMBAT_STILL_SINCE.remove(bot.getObjectId());
			return false;
		}

		long now = System.currentTimeMillis();
		Long since = PhantomState.COMBAT_STILL_SINCE.putIfAbsent(bot.getObjectId(), now);
		if ((since == null) || ((now - since) < 10000))
		{
			return false;
		}

		// Quieto 10s con el objetivo fuera de alcance = bloqueado por un obstaculo (arbol/roca): abandona ese mob 60s y busca otro.
		PhantomState.COMBAT_STILL_SINCE.remove(bot.getObjectId());
		PhantomState.IGNORED_MOB.put(bot.getObjectId(), target.getObjectId());
		PhantomState.IGNORED_MOB_UNTIL.put(bot.getObjectId(), now + 60000);
		bot.setTarget(null);
		bot.getAI().setIntention(Intention.ACTIVE);
		PhantomManager.logToFile(bot.getName(), "Objetivo inalcanzable tras obstaculo. Abandona mob: " + target.getName());
		return true;
	}

	private static boolean pickupNearbyDrops(Player bot)
	{
		for (Item drop : World.getInstance().getVisibleObjectsInRange(bot, Item.class, 400))
		{
			// Solo drops propios bajo proteccion (15s): un phantom nunca roba el loot de un jugador real.
			if ((drop != null) && drop.isSpawned() && drop.getDropProtection().isProtected() && (drop.getDropProtection().getOwner() == bot))
			{
				PhantomManager.logToFile(bot.getName(), "Recogiendo drop: " + drop.getName());
				bot.getAI().setIntention(Intention.PICK_UP, drop);
				return true;
			}
		}
		return false;
	}

	private static Monster findFarmTarget(Player bot)
	{
		List<Monster> idealMobs = new ArrayList<>();
		List<Monster> backupMobs = new ArrayList<>();
		boolean foundMobs = false;
		
		for (Monster m : World.getInstance().getVisibleObjectsInRange(bot, Monster.class, 2500))
		{
			if (!m.isDead() && m.isAttackable() && !isForbiddenFarmTarget(m))
			{
				foundMobs = true;
				List<Location> anchors = PhantomState.getAnchors(m.getLevel());
				anchors.add(new Location(m.getX(), m.getY(), m.getZ()));
				if (anchors.size() > 50)
				{
					anchors.remove(0);
				}
				
				Integer ignoredId = PhantomState.IGNORED_MOB.get(bot.getObjectId());
				if ((ignoredId != null) && (ignoredId.intValue() == m.getObjectId()) && (System.currentTimeMillis() < PhantomState.IGNORED_MOB_UNTIL.getOrDefault(bot.getObjectId(), 0L)))
				{
					continue;
				}

				if (!isTargetedByOtherPhantom(bot, m))
				{
					int lvlDiff = m.getLevel() - bot.getLevel();
					if ((lvlDiff >= -8) && (lvlDiff <= 2))
					{
						idealMobs.add(m);
					}
					else
					{
						backupMobs.add(m);
					}
				}
			}
		}
		
		if (!foundMobs)
		{
			int stuckCount = PhantomState.STUCK_COUNTERS.getOrDefault(bot.getObjectId(), 0) + 1;
			PhantomState.STUCK_COUNTERS.put(bot.getObjectId(), stuckCount);
			PhantomManager.logToFile(bot.getName(), "No encuentra mobs. Contador atascado: " + stuckCount);
			return null;
		}
		
		PhantomState.STUCK_COUNTERS.put(bot.getObjectId(), 0);
		if (idealMobs.isEmpty() && backupMobs.isEmpty())
		{
			PhantomManager.logToFile(bot.getName(), "Hay mobs cerca, pero todos estan ocupados por otros phantoms.");
			return null;
		}
		return !idealMobs.isEmpty() ? getNearestMob(bot, idealMobs) : (!backupMobs.isEmpty() ? getNearestMob(bot, backupMobs) : null);
	}
	
	private static boolean isForbiddenFarmTarget(Monster monster)
	{
		return PhantomHuntingSpots.isForbiddenTargetName(monster.getName());
	}
	
	private static Monster getNearestMob(Player bot, List<Monster> mobs)
	{
		Monster closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (Monster mob : mobs)
		{
			double distance = LocationUtil.calculateDistance(bot, mob, true, false);
			if (distance < closestDistance)
			{
				closest = mob;
				closestDistance = distance;
			}
		}
		return closest;
	}
	
	private static void handleNoFarmTarget(Player bot)
	{
		int objectId = bot.getObjectId();
		int emptyCount = PhantomState.EMPTY_TARGET_COUNTERS.getOrDefault(objectId, 0) + 1;
		PhantomState.EMPTY_TARGET_COUNTERS.put(objectId, emptyCount);
		
		if (emptyCount >= EMPTY_TARGET_RELOCATE_LIMIT)
		{
			PhantomState.EMPTY_TARGET_COUNTERS.put(objectId, 0);
			PhantomState.HUNT_LEVEL_BAND.put(objectId, -1);
			PhantomState.NEXT_HUNT_TELEPORT.put(objectId, 0L);
			if (PhantomHuntingSpots.travelToLevelSpot(bot, "Sin mobs utiles. Busca otro spot L" + bot.getLevel()))
			{
				trace(bot, "reubicado por falta de mobs utiles");
				return;
			}
		}
		
		trace(bot, "sin objetivo util, espera");
	}
	
	private static boolean isTargetedByOtherPhantom(Player bot, Monster monster)
	{
		if (LocationUtil.calculateDistance(bot, monster, true, false) <= 450)
		{
			return false;
		}
		for (Player p : PhantomEngine.activePhantoms)
		{
			if ((p != bot) && (p.getTarget() == monster))
			{
				return true;
			}
		}
		return false;
	}
	
	private static void runAwayFrom(Player bot, Creature danger)
	{
		int dx = bot.getX() - danger.getX();
		int dy = bot.getY() - danger.getY();
		if ((dx == 0) && (dy == 0))
		{
			dx = Rnd.get(-1, 1);
			dy = Rnd.get(-1, 1);
		}
		
		int newX = bot.getX() + (dx > 0 ? 1400 : -1400);
		int newY = bot.getY() + (dy > 0 ? 1400 : -1400);
		Location destination = GeoEngine.getInstance().getValidLocation(bot.getX(), bot.getY(), bot.getZ(), newX, newY, GeoEngine.getInstance().getHeight(newX, newY, bot.getZ()), bot.getInstanceId());
		bot.getAI().setIntention(Intention.MOVE_TO, destination);
		PhantomManager.logToFile(bot.getName(), "Sin MP en PvP. Escapando de " + danger.getName());
	}
	
	private static void executeAttackPlan(Player bot, Creature target)
	{
		if (!bot.isChargedShot(ShotType.SOULSHOTS))
		{
			bot.setChargedShot(ShotType.SOULSHOTS, true);
		}
		if (!bot.isChargedShot(ShotType.BLESSED_SPIRITSHOTS))
		{
			bot.setChargedShot(ShotType.BLESSED_SPIRITSHOTS, true);
		}
		
		List<Skill> offensiveSkills = new ArrayList<>();
		for (Skill sk : bot.getAllSkills())
		{
			if ((sk != null) && sk.hasNegativeEffect() && !sk.isPassive())
			{
				offensiveSkills.add(sk);
			}
		}
		
		// El servitor asiste al dueno en su objetivo, como un summoner real.
		if ((bot.getSummon() != null) && !bot.getSummon().isDead() && ((bot.getSummon().getAI().getIntention() != Intention.ATTACK) || (bot.getSummon().getTarget() != target)))
		{
			bot.getSummon().getAI().setIntention(Intention.ATTACK, target);
		}

		// Los magos nukean (85% si hay mana); los fighters solo meten skills fisicas de vez en cuando (25%).
		final int castChance = bot.isMageClass() ? 85 : 25;
		if (!offensiveSkills.isEmpty() && (bot.getCurrentMpPercent() > MP_REST_PERCENT) && (Rnd.get(100) < castChance))
		{
			PhantomManager.logToFile(bot.getName(), "Casteando contra " + target.getName());
			bot.getAI().setIntention(Intention.CAST, offensiveSkills.get(Rnd.get(offensiveSkills.size())), target);
		}
		else
		{
			PhantomManager.logToFile(bot.getName(), "Ataque fisico contra " + target.getName());
			if ((bot.getAI().getIntention() != Intention.ATTACK) || (bot.getTarget() != target))
			{
				// En HF el auto-ataque sostenido lo mantiene la intencion ATTACK del AI (equivale al doAutoAttack de Essence) y gestiona tambien la aproximacion.
				bot.getAI().setIntention(Intention.ATTACK, target);
			}
		}
	}
	
	private static void trace(Player bot, String message)
	{
		long now = System.currentTimeMillis();
		long last = PhantomState.LAST_AI_TRACE.getOrDefault(bot.getObjectId(), 0L);
		if ((now - last) >= 15000L)
		{
			PhantomState.LAST_AI_TRACE.put(bot.getObjectId(), now);
			PhantomManager.logToFile(bot.getName(), "AI " + message);
		}
	}
}
