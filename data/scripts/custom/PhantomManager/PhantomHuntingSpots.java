package custom.PhantomManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PhantomHuntingSpots
{
	// Antes de este nivel un jugador real levea en la region de su raza: spots, descansos y revives se anclan a la aldea natal (evita orcos/kamael/enanos farmeando el bosque elfico).
	public static final int RACIAL_AREA_MAX_LEVEL = 20;
	// Radio desde la aldea natal que cubre la region newbie de cada raza (islas incluidas) sin invadir la vecina.
	private static final int RACIAL_AREA_RADIUS = 25000;

	private static final Map<Integer, Integer> NPC_LEVELS = new HashMap<>();
	private static final Map<Integer, String> NPC_NAMES = new HashMap<>();
	private static final Map<Integer, List<Location>> SPOTS_BY_LEVEL = new HashMap<>();
	private static boolean _loaded = false;
	
	public static synchronized void load()
	{
		if (_loaded)
		{
			return;
		}
		
		NPC_LEVELS.clear();
		NPC_NAMES.clear();
		SPOTS_BY_LEVEL.clear();
		loadNpcLevels(new File("data/stats/npcs"));
		loadSpawnSpots(new File("data/spawns"));
		_loaded = true;
		
		int total = 0;
		for (List<Location> spots : SPOTS_BY_LEVEL.values())
		{
			total += spots.size();
		}
		System.out.println(">>> [PHANTOM SYSTEM] Spots de leveo cargados desde datapack: " + total + " coordenadas en " + SPOTS_BY_LEVEL.size() + " niveles.");
	}
	
	public static boolean relocateForLevelIfNeeded(Player bot)
	{
		int band = getLevelBand(bot.getLevel());
		int objectId = bot.getObjectId();
		if (System.currentTimeMillis() < PhantomState.NEXT_HUNT_TELEPORT.getOrDefault(objectId, 0L))
		{
			return false;
		}
		if (PhantomState.HUNT_LEVEL_BAND.getOrDefault(objectId, -1) == band)
		{
			return false;
		}
		
		return travelToLevelSpot(bot, "Viajando a spot de leveo L" + bot.getLevel());
	}
	
	public static boolean travelToLevelSpot(Player bot, String reason)
	{
		Location spot = getUncrowdedSpot(bot);
		if (spot == null)
		{
			// Fallback a la zona fija del config si el indice de spots no cargo: nunca dejar al phantom clavado en ciudad.
			PhantomConfig.FarmZone zone = PhantomConfig.getIdealZone(bot.getLevel());
			spot = new Location(zone.spotX, zone.spotY, zone.spotZ);
		}

		Player observer = PhantomEngine.getNearestRealObserver(bot, PhantomConfig.REAL_OBSERVER_RADIUS);
		if (observer != null)
		{
			// Teleport timido: con un jugador real mirando, se aleja andando y teleporta al perderse de vista.
			PhantomEngine.walkAwayFrom(bot, observer);
			return true;
		}

		PhantomState.HUNT_LEVEL_BAND.put(bot.getObjectId(), getLevelBand(bot.getLevel()));
		Location safe = PhantomGeo.getSafeSpawn(spot, 250);
		PhantomEngine.movePhantomTo(bot, safe, reason);
		PhantomManager.logToFile(bot.getName(), reason + ": " + safe.getX() + ", " + safe.getY() + ", " + safe.getZ());
		return true;
	}
	
	public static void markAtLevelSpot(Player bot)
	{
		PhantomState.HUNT_LEVEL_BAND.put(bot.getObjectId(), getLevelBand(bot.getLevel()));
		PhantomState.NEXT_HUNT_TELEPORT.put(bot.getObjectId(), System.currentTimeMillis() + 60000L);
	}

	public static Location getUncrowdedSpot(Player bot)
	{
		List<Location> candidates = getCandidateSpots(bot);
		if (candidates.isEmpty())
		{
			return null;
		}

		// Elige entre los 12 spots MAS CERCANOS no masificados: un jugador real caza cerca de donde esta, y al subir de nivel migra solo (sus spots se alejan).
		// Posicion del bot congelada antes de ordenar: se mueve en otro hilo y un comparador en vivo puede violar el contrato de TimSort.
		final int botX = bot.getX();
		final int botY = bot.getY();
		candidates.sort(Comparator.comparingLong(spot ->
		{
			final long dx = spot.getX() - botX;
			final long dy = spot.getY() - botY;
			return (dx * dx) + (dy * dy);
		}));
		List<Location> nearest = candidates.subList(0, Math.min(12, candidates.size()));
		List<Location> free = new ArrayList<>();
		for (Location spot : nearest)
		{
			// Ni masificado de phantoms ni a la vista de un jugador real: aparecer por teleport delante de alguien canta tanto como esfumarse.
			if ((PhantomEngine.countPhantomsNear(spot, 1200) < 3) && !PhantomEngine.isSpotObservedByRealPlayer(spot, PhantomConfig.REAL_OBSERVER_RADIUS))
			{
				free.add(spot);
			}
		}
		List<Location> pool = free.isEmpty() ? nearest : free;
		return pool.get(Rnd.get(pool.size()));
	}

	public static Location getRandomSpot(int playerLevel)
	{
		List<Location> candidates = getCandidateSpots(playerLevel, null);
		return candidates.isEmpty() ? null : candidates.get(Rnd.get(candidates.size()));
	}

	private static List<Location> getCandidateSpots(Player bot)
	{
		if (bot.getLevel() < RACIAL_AREA_MAX_LEVEL)
		{
			// Anclaje racial: solo spots de la region natal. Si el indice no tuviera ninguno, cae al pool global antes que dejar al bot sin destino.
			List<Location> homeCandidates = getCandidateSpots(bot.getLevel(), bot.getTemplate().getCreationPoint());
			if (!homeCandidates.isEmpty())
			{
				return homeCandidates;
			}
		}
		return getCandidateSpots(bot.getLevel(), null);
	}

	private static List<Location> getCandidateSpots(int playerLevel, Location home)
	{
		for (int range = 0; range <= 12; range++)
		{
			List<Location> candidates = new ArrayList<>();
			int minLevel = Math.max(1, playerLevel - 7 - range);
			int maxLevel = playerLevel + 2 + range;
			for (int level = minLevel; level <= maxLevel; level++)
			{
				List<Location> spots = SPOTS_BY_LEVEL.get(level);
				if (spots != null)
				{
					for (Location spot : spots)
					{
						if ((home == null) || isNearHome(spot, home))
						{
							candidates.add(spot);
						}
					}
				}
			}
			if (!candidates.isEmpty())
			{
				return candidates;
			}
		}
		return new ArrayList<>();
	}

	private static boolean isNearHome(Location spot, Location home)
	{
		final long dx = spot.getX() - home.getX();
		final long dy = spot.getY() - home.getY();
		return ((dx * dx) + (dy * dy)) <= ((long) RACIAL_AREA_RADIUS * RACIAL_AREA_RADIUS);
	}
	
	private static int getLevelBand(int level)
	{
		return Math.max(1, (level / 5) * 5);
	}
	
	private static void loadNpcLevels(File dir)
	{
		File[] files = dir.listFiles();
		if (files == null)
		{
			return;
		}
		
		for (File file : files)
		{
			if (file.isDirectory())
			{
				loadNpcLevels(file);
			}
			else if (file.getName().endsWith(".xml"))
			{
				parseNpcFile(file);
			}
		}
	}
	
	private static void parseNpcFile(File file)
	{
		try
		{
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			NodeList npcNodes = doc.getElementsByTagName("npc");
			for (int i = 0; i < npcNodes.getLength(); i++)
			{
				Element npc = (Element) npcNodes.item(i);
				// En los XML de High Five type="Monster" ya implica atacable; el <status attackable=""> de Essence no existe y dejaba el indice VACIO.
				if (!"Monster".equalsIgnoreCase(npc.getAttribute("type")))
				{
					continue;
				}
				if (npc.getAttribute("level").isEmpty())
				{
					continue;
				}

				int id = Integer.parseInt(npc.getAttribute("id"));
				int level = Integer.parseInt(npc.getAttribute("level"));
				String name = npc.getAttribute("name");
				if (isForbiddenTargetName(name))
				{
					continue;
				}
				// Mismo criterio que el core (NpcTemplate: title.contains("Quest")): no indexar zonas de mobs de quest como spots de farmeo, no dan experiencia.
				if (npc.getAttribute("title").contains("Quest"))
				{
					continue;
				}
				NPC_LEVELS.put(id, level);
				NPC_NAMES.put(id, name);
			}
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("SPOT_LOADER", "Error leyendo NPCs " + file.getName() + ": " + e.getMessage());
		}
	}
	
	private static void loadSpawnSpots(File dir)
	{
		File[] files = dir.listFiles();
		if (files == null)
		{
			return;
		}
		
		for (File file : files)
		{
			if (file.isDirectory())
			{
				loadSpawnSpots(file);
			}
			else if (file.getName().endsWith(".xml") && !file.getName().toLowerCase().contains("raid"))
			{
				parseSpawnFile(file);
			}
		}
	}
	
	private static void parseSpawnFile(File file)
	{
		try
		{
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			NodeList spawnNodes = doc.getElementsByTagName("spawn");
			for (int s = 0; s < spawnNodes.getLength(); s++)
			{
				Element spawn = (Element) spawnNodes.item(s);

				// Centroide del territorio: los spawns territoriales de HF (zone=) no llevan coordenadas por npc, pero definen su poligono en el propio fichero.
				int territoryX = 0;
				int territoryY = 0;
				int territoryZ = 0;
				int nodeCount = 0;
				NodeList territories = spawn.getElementsByTagName("territory");
				if (territories.getLength() > 0)
				{
					Element territory = (Element) territories.item(0);
					NodeList nodes = territory.getElementsByTagName("node");
					for (int n = 0; n < nodes.getLength(); n++)
					{
						Element node = (Element) nodes.item(n);
						territoryX += Integer.parseInt(node.getAttribute("x"));
						territoryY += Integer.parseInt(node.getAttribute("y"));
						nodeCount++;
					}
					if (nodeCount > 0)
					{
						territoryX /= nodeCount;
						territoryY /= nodeCount;
						int minZ = territory.getAttribute("minZ").isEmpty() ? 0 : Integer.parseInt(territory.getAttribute("minZ"));
						int maxZ = territory.getAttribute("maxZ").isEmpty() ? minZ : Integer.parseInt(territory.getAttribute("maxZ"));
						territoryZ = (minZ + maxZ) / 2;
					}
				}

				NodeList npcNodes = spawn.getElementsByTagName("npc");
				for (int i = 0; i < npcNodes.getLength(); i++)
				{
					Element npc = (Element) npcNodes.item(i);
					int id = Integer.parseInt(npc.getAttribute("id"));
					Integer level = NPC_LEVELS.get(id);
					if (level == null)
					{
						continue;
					}
					if (isForbiddenTargetName(NPC_NAMES.get(id)))
					{
						continue;
					}

					int x;
					int y;
					int z;
					if (!npc.getAttribute("x").isEmpty())
					{
						x = Integer.parseInt(npc.getAttribute("x"));
						y = Integer.parseInt(npc.getAttribute("y"));
						z = Integer.parseInt(npc.getAttribute("z"));
					}
					else if (nodeCount > 0)
					{
						x = territoryX;
						y = territoryY;
						z = territoryZ;
					}
					else
					{
						continue;
					}

					List<Location> spots = SPOTS_BY_LEVEL.computeIfAbsent(level, k -> new ArrayList<>());
					if (spots.size() < 1000)
					{
						spots.add(new Location(x, y, z));
					}
				}
			}
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("SPOT_LOADER", "Error leyendo spawns " + file.getName() + ": " + e.getMessage());
		}
	}
	
	public static boolean isForbiddenTargetName(String name)
	{
		if (name == null)
		{
			return false;
		}
		
		String lowerName = name.toLowerCase();
		return lowerName.contains("training") || lowerName.contains("dummy") || lowerName.contains("practice") || lowerName.contains("tutorial") || lowerName.contains("event") || lowerName.contains("chest") || lowerName.contains("treasure box");
	}
}
