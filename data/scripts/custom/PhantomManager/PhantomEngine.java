package custom.PhantomManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.MultilingualSupportConfig;
import org.l2jmobius.gameserver.data.sql.CharSummonTable;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportWhereType;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.serverpackets.ValidateLocation;

public class PhantomEngine
{
	public static final List<Player> activePhantoms = new CopyOnWriteArrayList<>();
	public static volatile boolean isRunning = false;
	private static final AtomicBoolean _deleteAllInProgress = new AtomicBoolean(false);
	
	public static void startSystem(Player gm)
	{
		startBatch(10, gm);
	}
	
	public static synchronized void startBatch(int count, Player gm)
	{
		if (isRunning)
		{
			PhantomManager.logToFile("SYSTEM", "Sistema ya estaba corriendo. Agregando lote de " + count + ".");
		}
		else
		{
			PhantomManager.startLogSession("PHANTOM_START");
			isRunning = true;
		}
		PhantomManager.logToFile("SYSTEM", "Iniciando lote de hasta " + count + " phantoms. IDs configurados=" + PhantomConfig.PHANTOM_IDS.size());
		final int loadedCount = bringOnlineInternal(count, false);
		if (gm != null)
		{
			gm.sendMessage(">>> Se iniciaron " + loadedCount + " phantoms en este lote.");
			PhantomMenu.showMenu(gm);
		}
	}

	/**
	 * Conecta hasta {@code count} phantoms del pool XML que no esten ya online.
	 * @param count numero maximo a conectar
	 * @param withSession si {@code true} cada phantom recibe una hora de fin de sesion (gestion automatica de poblacion)
	 * @return cuantos se conectaron
	 */
	private static int bringOnlineInternal(int count, boolean withSession)
	{
		int loadedCount = 0;
		for (int charId : PhantomConfig.PHANTOM_IDS)
		{
			if (loadedCount >= count)
			{
				break;
			}
			try
			{
				if (World.getInstance().getPlayer(charId) != null)
				{
					continue;
				}

				final Player phantom = Player.load(charId);
				if (phantom == null)
				{
					continue;
				}

				preparePhantom(phantom, false);
				registerPhantom(phantom, Rnd.get(100) < 10, Rnd.get(100) < 3);
				if (withSession)
				{
					PhantomState.SESSION_END.put(phantom.getObjectId(), System.currentTimeMillis() + Rnd.get((int) PhantomConfig.SESSION_MIN_MS, (int) PhantomConfig.SESSION_MAX_MS));
				}
				startPhantomAI(phantom);
				PhantomManager.logToFile(phantom.getName(), "Conectado desde XML. online=" + phantom.isOnline() + " spawned=" + phantom.isSpawned() + " loc=" + phantom.getX() + "," + phantom.getY() + "," + phantom.getZ());
				loadedCount++;
			}
			catch (Exception e)
			{
				PhantomManager.logException("SPAWN", "Error al spawnear charId " + charId, e);
			}
		}
		return loadedCount;
	}

	/**
	 * Punto de entrada del gestor automatico de poblacion: conecta un lote con sesiones.
	 * @param count numero maximo a conectar
	 * @return cuantos se conectaron
	 */
	public static synchronized int bringOnline(int count)
	{
		if (_deleteAllInProgress.get())
		{
			// Durante el borrado total (que no toma el monitor de clase durante su drenaje/borrado en BD) el gestor no debe reconectar phantoms: sus filas se estan borrando y quedarian en mundo sin registro en BD.
			return 0;
		}
		if (!isRunning)
		{
			PhantomManager.startLogSession("PHANTOM_POPULATION");
			isRunning = true;
		}
		return bringOnlineInternal(count, true);
	}

	/**
	 * Desconecta un phantom concreto (fin de sesion). No borra su personaje: sigue en el pool XML.
	 * @param phantom el phantom a desconectar
	 */
	public static synchronized void logoutPhantom(Player phantom)
	{
		if (phantom == null)
		{
			return;
		}
		PhantomManager.logToFile(phantom.getName(), "Fin de sesion. Se desconecta.");
		PhantomState.unregister(phantom.getObjectId());
		activePhantoms.remove(phantom);
		phantom.deleteMe();
	}
	
	public static synchronized void createAndStart(int count, Player gm)
	{
		createAndStart(count, 0, 0, gm);
	}

	public static synchronized void createAndStart(int count, int minLevel, int maxLevel, Player gm)
	{
		PhantomManager.startLogSession("PHANTOM_CREATE_" + count);
		if (!isRunning)
		{
			isRunning = true;
		}

		int createdCount = 0;
		for (Player phantom : PhantomFactory.createPhantoms(count, minLevel, maxLevel))
		{
			try
			{
				preparePhantom(phantom, true);
				registerPhantom(phantom, Rnd.get(100) < 17, Rnd.get(100) < 6);
				PhantomConfig.addPhantomId(phantom.getObjectId());
				startPhantomAI(phantom);
				PhantomManager.logToFile(phantom.getName(), "IA programada desde creacion. online=" + phantom.isOnline() + " spawned=" + phantom.isSpawned() + " loc=" + phantom.getX() + "," + phantom.getY() + "," + phantom.getZ());
				createdCount++;
			}
			catch (Exception e)
			{
				PhantomManager.logException("AUTO_SPAWN", "Error iniciando phantom", e);
			}
		}
		
		if (gm != null)
		{
			gm.sendMessage("Se crearon e iniciaron " + createdCount + " phantoms automaticos.");
			PhantomManager.logToFile("AUTO_CREATE", "Se crearon " + createdCount + " phantoms automaticos.");
			PhantomMenu.showMenu(gm);
		}
	}
	
	public static synchronized int stopSome(int count, Player gm)
	{
		int stopped = 0;
		for (Player p : activePhantoms)
		{
			if (stopped >= count)
			{
				break;
			}
			if (p != null)
			{
				PhantomManager.logToFile(p.getName(), "Desconectado por lote GM.");
				PhantomState.unregister(p.getObjectId());
				activePhantoms.remove(p);
				p.deleteMe();
				stopped++;
			}
		}
		
		if (activePhantoms.isEmpty())
		{
			isRunning = false;
		}
		if (gm != null)
		{
			gm.sendMessage("Se desconectaron " + stopped + " phantoms.");
			PhantomMenu.showMenu(gm);
		}
		return stopped;
	}
	
	public static synchronized void stopSystem(Player gm)
	{
		if (!isRunning)
		{
			return;
		}

		// Para tambien el gestor automatico: un .pstop del GM no debe ser repoblado a los 90s.
		PhantomPopulation.stop();
		PhantomManager.logToFile("SYSTEM", "Deteniendo sistema. Phantoms activos=" + activePhantoms.size());
		isRunning = false;
		for (Player p : activePhantoms)
		{
			if (p != null)
			{
				p.deleteMe();
			}
		}
		
		activePhantoms.clear();
		PhantomState.clear();
		
		if (gm != null)
		{
			gm.sendMessage(">>> Sistema DETENIDO.");
		}
	}
	
	/**
	 * Borrado total: desconecta todos los phantoms, elimina de la BD todos los personajes de la cuenta de phantoms (incluidos huerfanos fuera del XML) y vacia el pool XML.
	 * @param gm el GM que ordena el borrado
	 * @return cuantos personajes se eliminaron de la BD
	 */
	public static int deleteAllPhantoms(Player gm)
	{
		// Sin synchronized: el drenaje + borrado masivo puede durar segundos y bloquearia al gestor de poblacion y a todos los comandos GM que comparten el monitor de clase. El guard atomico evita dos borrados simultaneos; solo stopSystem toma el monitor.
		if (!_deleteAllInProgress.compareAndSet(false, true))
		{
			if (gm != null)
			{
				gm.sendMessage("Ya hay un borrado total en curso.");
			}
			return 0;
		}
		try
		{
			PhantomManager.startLogSession("PHANTOM_DELETE_ALL");
			stopSystem(null);
			try
			{
				// Drenaje: margen para que los ticks en vuelo (corren en ms) terminen antes de borrar en BD — un INSERT tardio (skill/item) re-crearia filas del personaje ya borrado.
				Thread.sleep(1500);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}

			int deleted = 0;
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("DELETE FROM character_summons WHERE ownerId=?"))
			{
				for (int charId : PhantomFactory.getAllPhantomCharIds())
				{
					try
					{
						// Mismo borrado en cascada que usa el cliente al eliminar un personaje (items, skills, subclases, etc.).
						GameClient.deleteCharByObjId(charId);
						// El cascade del core no cubre character_summons y los phantoms invocadores persisten ahi su servitor al desconectar.
						ps.setInt(1, charId);
						ps.execute();
						deleted++;
					}
					catch (Exception e)
					{
						PhantomManager.logException("DELETE_ALL", "Error borrando charId " + charId, e);
					}
				}
			}
			catch (Exception e)
			{
				PhantomManager.logException("DELETE_ALL", "Error en el borrado total", e);
			}

			PhantomConfig.clearAllIds();
			PhantomManager.logToFile("SYSTEM", "Borrado total: " + deleted + " personajes eliminados de la BD y pool XML vaciado.");
			if (gm != null)
			{
				gm.sendMessage(">>> Se eliminaron " + deleted + " phantoms de la BD. Pool XML vaciado.");
			}
			return deleted;
		}
		finally
		{
			_deleteAllInProgress.set(false);
		}
	}

	public static Player getPhantomByName(String name)
	{
		return activePhantoms.stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public static void setOnlineFlag(Player phantom, boolean online)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE characters SET online=? WHERE charId=?"))
		{
			ps.setInt(1, online ? 1 : 0);
			ps.setInt(2, phantom.getObjectId());
			ps.execute();
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("SYSTEM", "No se pudo actualizar el flag online de " + phantom.getName() + ": " + e.getMessage());
		}
	}

	public static void applyOnlineFlagToAll(boolean online)
	{
		for (Player phantom : activePhantoms)
		{
			if (phantom != null)
			{
				setOnlineFlag(phantom, online);
			}
		}
	}

	public static boolean isObservedByRealPlayer(Player phantom, int radius)
	{
		return getNearestRealObserver(phantom, radius) != null;
	}

	public static Player getNearestRealObserver(Player phantom, int radius)
	{
		Player nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Player p : World.getInstance().getVisibleObjectsInRange(phantom, Player.class, radius))
		{
			if (!p.isGM() && !activePhantoms.contains(p))
			{
				double distance = phantom.calculateDistance2D(p);
				if (distance < nearestDistance)
				{
					nearestDistance = distance;
					nearest = p;
				}
			}
		}
		return nearest;
	}

	public static void walkAwayFrom(Player bot, Player observer)
	{
		int dx = bot.getX() - observer.getX();
		int dy = bot.getY() - observer.getY();
		if ((dx == 0) && (dy == 0))
		{
			dx = Rnd.get(-1, 1);
			dy = Rnd.get(-1, 1);
		}
		int newX = bot.getX() + (dx > 0 ? 1200 : -1200);
		int newY = bot.getY() + (dy > 0 ? 1200 : -1200);
		Location destination = GeoEngine.getInstance().getValidLocation(bot.getX(), bot.getY(), bot.getZ(), newX, newY, GeoEngine.getInstance().getHeight(newX, newY, bot.getZ()), bot.getInstanceId());
		bot.getAI().setIntention(Intention.MOVE_TO, destination);
	}

	public static int countPhantomsNear(Location loc, int radius)
	{
		int count = 0;
		for (Player p : activePhantoms)
		{
			if ((p != null) && !p.isDead() && (p.calculateDistance2D(loc) < radius))
			{
				count++;
			}
		}
		return count;
	}
	
	public static void movePhantomTo(Player phantom, Location loc, String reason)
	{
		movePhantomTo(phantom, loc, 0, reason);
	}
	
	public static void movePhantomTo(Player phantom, Location loc, int instanceId, String reason)
	{
		if ((phantom == null) || (loc == null))
		{
			return;
		}
		
		phantom.abortAttack();
		phantom.abortCast();
		phantom.setTarget(null);
		phantom.setInstanceId(instanceId);
		phantom.setInvisible(false);
		phantom.setRunning();
		phantom.setXYZ(loc.getX(), loc.getY(), loc.getZ());
		if (loc.getHeading() != 0)
		{
			phantom.setHeading(loc.getHeading());
		}
		if (!phantom.isSpawned())
		{
			phantom.spawnMe(loc.getX(), loc.getY(), loc.getZ());
		}
		else
		{
			phantom.broadcastInfo();
		}
		phantom.broadcastUserInfo();
		// El teleport crudo (setXYZ) no arrastra al servitor como haria teleToLocation: moverlo junto a su dueno o se queda plantado en el spot viejo.
		if ((phantom.getSummon() != null) && !phantom.getSummon().isDead())
		{
			phantom.getSummon().teleToLocation(loc.getX() + Rnd.get(-60, 60), loc.getY() + Rnd.get(-60, 60), loc.getZ());
		}
		PhantomManager.logToFile(phantom.getName(), reason + ": " + loc.getX() + ", " + loc.getY() + ", " + loc.getZ() + " spawned=" + phantom.isSpawned());
	}
	
	private static void preparePhantom(Player phantom, boolean creationSpawn)
	{
		// Sin cliente nadie fija el idioma: con multilang activo un lang null revienta la cadena de dano entera (NPE en NpcNameLocalisationData desde sendDamageMessage).
		phantom.setLang(MultilingualSupportConfig.MULTILANG_DEFAULT);
		phantom.setOnlineStatus(true, true);
		if (PhantomConfig.COUNT_ONLINE_WEB)
		{
			// updateOnlineStatus escribe isOnlineInt()=0 al no haber cliente: sin este UPDATE la web los mostraria siempre offline.
			setOnlineFlag(phantom, true);
		}
		phantom.setRunning();
		phantom.setInvul(false);
		phantom.setInvisible(false);
		phantom.setInstanceId(0);
		phantom.setEnteredWorld();
		if (creationSpawn)
		{
			spawnAtCreationPoint(phantom);
		}
		else
		{
			spawnPhantom(phantom);
		}
		// La restauracion del servitor vive en EnterWorld (cliente): sin esto, la entrada de character_summons del logout anterior bloquea el canSummon PARA SIEMPRE.
		if (CharSummonTable.getInstance().getServitors().containsKey(phantom.getObjectId()))
		{
			CharSummonTable.getInstance().restoreServitor(phantom);
		}
		phantom.broadcastUserInfo();
	}
	
	private static void registerPhantom(Player phantom, boolean aggressive, boolean pkMode)
	{
		pkMode = pkMode && PhantomConfig.PK_ENABLED; // El rasgo PK es desactivable desde .pmenu.
		if (!activePhantoms.contains(phantom))
		{
			activePhantoms.add(phantom);
		}
		
		PhantomState.register(phantom.getObjectId(), aggressive, pkMode);
		// El rasgo PK queda latente por debajo del nivel minimo: se volveran rojos en combate cuando les toque, como un PK real.
		if (pkMode && (phantom.getLevel() >= PhantomConfig.PK_MIN_LEVEL))
		{
			phantom.setPkKills(Math.max(1, phantom.getPkKills()));
			phantom.setKarma(Rnd.get(360, 3600));
			phantom.updatePvpTitleAndColor(true);
			phantom.broadcastKarma();
			phantom.broadcastUserInfo();
		}
	}
	
	private static void spawnPhantom(Player phantom)
	{
		Location spot = PhantomHuntingSpots.getUncrowdedSpot(phantom);
		int spawnX;
		int spawnY;
		int spawnZ;
		if (spot != null)
		{
			Location safe = PhantomGeo.getSafeSpawn(spot, 250);
			spawnX = safe.getX();
			spawnY = safe.getY();
			spawnZ = safe.getZ();
		}
		else
		{
			PhantomConfig.FarmZone zone = PhantomConfig.getIdealZone(phantom.getLevel());
			Location safe = PhantomGeo.getNpcLikeSpawn(new Location(zone.townX, zone.townY, zone.townZ));
			spawnX = safe.getX();
			spawnY = safe.getY();
			spawnZ = safe.getZ();
		}
		
		if (phantom.isSpawned())
		{
			movePhantomTo(phantom, new Location(spawnX, spawnY, spawnZ), "Reposicionado en spot NPC");
		}
		else
		{
			phantom.spawnMe(spawnX, spawnY, spawnZ);
		}
		PhantomHuntingSpots.markAtLevelSpot(phantom);
		PhantomManager.logToFile(phantom.getName(), "Spawn visible en spot NPC: " + spawnX + ", " + spawnY + ", " + spawnZ);
	}
	
	private static void spawnAtCreationPoint(Player phantom)
	{
		Location point = phantom.getTemplate().getCreationPoint();
		Location safe = PhantomGeo.getNpcLikeSpawn(point);
		int spawnX = safe.getX();
		int spawnY = safe.getY();
		int spawnZ = safe.getZ();
		
		Location scattered = PhantomGeo.getSafeSpawn(new Location(spawnX, spawnY, spawnZ), 150);
		if (phantom.isSpawned())
		{
			movePhantomTo(phantom, scattered, "Reposicionado en ciudad de origen");
		}
		else
		{
			phantom.spawnMe(scattered.getX(), scattered.getY(), scattered.getZ());
		}
		PhantomState.HUNT_LEVEL_BAND.put(phantom.getObjectId(), -1);
		PhantomState.NEXT_HUNT_TELEPORT.put(phantom.getObjectId(), System.currentTimeMillis() + 60000L);
		PhantomManager.logToFile(phantom.getName(), "Nacido en ciudad de origen: " + scattered.getX() + ", " + scattered.getY() + ", " + scattered.getZ());
	}
	
	private static void teleportPhantomToTown(Player phantom)
	{
		// Antes del nivel 20 revive en su aldea natal: el pueblo mas cercano del MapRegion podia ser el de otra raza (muerte durante una deriva) y lo dejaba asentado en zona ajena.
		Location point = (phantom.getLevel() < PhantomHuntingSpots.RACIAL_AREA_MAX_LEVEL) ? phantom.getTemplate().getCreationPoint() : MapRegionData.getInstance().getTeleToLocation(phantom, TeleportWhereType.TOWN);
		Location safe = PhantomGeo.getSafeSpawn(point, 150);
		movePhantomTo(phantom, safe, "Revive en ciudad mas cercana");
		PhantomState.HUNT_LEVEL_BAND.put(phantom.getObjectId(), -1);
		PhantomState.NEXT_HUNT_TELEPORT.put(phantom.getObjectId(), System.currentTimeMillis() + 30000L);
	}
	
	public static void startPhantomAI(Player bot)
	{
		if ((bot == null) || !isRunning)
		{
			return;
		}
		
		ThreadPool.schedule(() ->
		{
			// Mata los ticks huerfanos: tras .pstop + .pstart rapido, el tick del Player borrado seguia vivo y lo re-spawneaba como fantasma duplicado del charId.
			// Comparacion por IDENTIDAD y no contains(): WorldObject.equals compara objectId, y si el mismo charId se reconecta dentro de la ventana del tick pendiente, el tick de la instancia VIEJA veria a la nueva y seguiria vivo (dos cadenas de IA para el mismo personaje).
			if (!isRunning || !isActiveInstance(bot))
			{
				return;
			}
			
			try
			{
				if (!bot.isSpawned() && !bot.isDead())
				{
					PhantomManager.logToFile(bot.getName(), "IA detecto phantom sin spawn. Reposicionando.");
					spawnPhantom(bot);
				}
				if (!bot.isDead())
				{
					traceAi(bot, "tick online=" + bot.isOnline() + " spawned=" + bot.isSpawned() + " moving=" + bot.isMoving() + " casting=" + bot.isCastingNow() + " attacking=" + bot.isAttackingNow() + " loc=" + bot.getX() + "," + bot.getY() + "," + bot.getZ());
					bot.broadcastPacket(new ValidateLocation(bot)); // Los observadores no validan la posicion de un jugador sin cliente; resincroniza para que los efectos (aura de level-up) caigan sobre el personaje.
					if (!isEngaged(bot))
					{
						// Mantenimiento (buffs, progresion, limpieza) solo fuera de combate: en pelea el tick rapido se dedica a encadenar acciones.
						PhantomEquipment.applyBasicBuffs(bot);
						PhantomEquipment.checkProgression(bot);
						PhantomEquipment.cleanInventory(bot);
					}
					PhantomAI.thinkAndFarm(bot);
				}
				else if (bot.isDead())
				{
					if (!PhantomState.REVIVING_PHANTOMS.add(bot.getObjectId()))
					{
						return;
					}
					
					if (Rnd.get(100) < 60)
					{
						ThreadPool.schedule(() ->
						{
							if (isRunning && activePhantoms.contains(bot) && bot.isOnline() && bot.isDead())
							{
								if (PhantomConfig.CHAT_ENABLED)
								{
									PhantomChat.botReply(bot, null, PhantomChat.pickDeathMessage(), false);
								}
							}
						}, Rnd.get(4000, 10000));
					}
					ThreadPool.schedule(() ->
					{
						try
						{
							if (isRunning && activePhantoms.contains(bot) && bot.isOnline())
							{
								teleportPhantomToTown(bot);
								bot.doRevive();
								bot.setCurrentHp(bot.getMaxHp());
								bot.setCurrentMp(bot.getMaxMp());
								bot.broadcastUserInfo();
								PhantomState.MP_RECOVERY_STATE.put(bot.getObjectId(), false);
							}
						}
						finally
						{
							PhantomState.REVIVING_PHANTOMS.remove(bot.getObjectId());
						}
					}, Rnd.get(15000, 25000));
				}
			}
			catch (Exception e)
			{
				PhantomManager.logException(bot.getName(), "Error en bucle IA", e);
				e.printStackTrace();
			}
			finally
			{
				startPhantomAI(bot);
			}
		}, nextTickDelay(bot));
	}

	private static int nextTickDelay(Player bot)
	{
		// En combate el bot decide rapido (encadena skills y recoge su loot sin pausas de robot); entre mob y mob mantiene la cadencia relajada e irregular (un tick fijo sincronizaria las decisiones de todos los phantoms).
		// Blindado: este calculo corre en el finally que re-agenda el tick, y una excepcion aqui mataria la cadena de IA del bot para siempre.
		try
		{
			return (isEngaged(bot) || hasPendingLoot(bot)) ? Rnd.get(900, 1700) : Rnd.get(3000, 6000);
		}
		catch (Exception e)
		{
			return Rnd.get(3000, 6000);
		}
	}

	/**
	 * Un phantom esta enfrascado cuando pega, castea, recoge o persigue un objetivo vivo. Solo checks baratos (sin escaneos de World): se evalua dos veces por tick.
	 * @param bot el phantom
	 * @return {@code true} si esta en plena accion
	 */
	private static boolean isEngaged(Player bot)
	{
		final Intention intention = bot.getAI().getIntention();
		if ((intention == Intention.CAST) || (intention == Intention.PICK_UP) || bot.isAttackingNow() || bot.isCastingNow())
		{
			return true;
		}
		// Lectura UNICA del target: otro hilo puede anularlo entre dos lecturas y el NPE tumbaria el re-agendado.
		final WorldObject target = bot.getTarget();
		return (intention == Intention.ATTACK) && (target instanceof Creature) && !((Creature) target).isDead();
	}

	/**
	 * Loot pendiente (drops propios protegidos en el suelo o cadaver spoileado sin barrer): merece cadencia rapida aunque el combate haya terminado, porque proteccion y decay se miden en segundos.
	 * @param bot el phantom
	 * @return {@code true} si hay botin esperando
	 */
	private static boolean hasPendingLoot(Player bot)
	{
		for (Item drop : World.getInstance().getVisibleObjectsInRange(bot, Item.class, 400))
		{
			if ((drop != null) && drop.isSpawned() && drop.getDropProtection().isProtected() && (drop.getDropProtection().getOwner() == bot))
			{
				return true;
			}
		}
		for (Monster corpse : World.getInstance().getVisibleObjectsInRange(bot, Monster.class, 400))
		{
			if (corpse.isDead() && corpse.isSweepActive() && (corpse.getSpoilerObjectId() == bot.getObjectId()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Busca la instancia EXACTA en la lista de activos (identidad, no equals por objectId).
	 * @param bot la instancia a comprobar
	 * @return {@code true} si esta instancia concreta sigue activa
	 */
	private static boolean isActiveInstance(Player bot)
	{
		for (Player p : activePhantoms)
		{
			if (p == bot)
			{
				return true;
			}
		}
		return false;
	}
	
	private static void traceAi(Player bot, String message)
	{
		// Throttle propio (LAST_ENGINE_TRACE): compartir clave con el trace() de PhantomAI hacia que este "tick online=..." (que corre siempre primero) consumiera la ventana de 15s y las trazas de decision no se escribieran nunca.
		long now = System.currentTimeMillis();
		long last = PhantomState.LAST_ENGINE_TRACE.getOrDefault(bot.getObjectId(), 0L);
		if ((now - last) >= 15000L)
		{
			PhantomState.LAST_ENGINE_TRACE.put(bot.getObjectId(), now);
			PhantomManager.logToFile(bot.getName(), "AI " + message);
		}
	}
}
