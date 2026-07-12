package custom.PhantomManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.l2jmobius.commons.util.Rnd;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class PhantomConfig
{
	private static final String PHANTOM_XML_PATH = "config/Custom/PhantomPlayers.xml";
	public static final List<Integer> PHANTOM_IDS = new CopyOnWriteArrayList<>();
	public static final List<FarmZone> ROUTES = new ArrayList<>();
	// Un PK de nivel 1 es imposible de justificar; el rasgo PK solo se manifiesta a partir de este nivel.
	public static final int PK_MIN_LEVEL = 20;

	// --- Piramide de niveles al crear: un servidor vivo no tiene a toda su poblacion a nivel 1. ---
	public static final int PYRAMID_MAX_LEVEL = 78;

	// --- Tramos horarios de densidad: % del pool online por franja (00-06 / 06-12 / 12-18 / 18-24). Independientes 0-100, editables desde .pmenu y persistidos en PhantomAI.ini. ---
	private static final String SETTINGS_PATH = "config/Custom/PhantomAI.ini";
	public static final int[] HOURLY_CURVE =
	{
		20,
		45,
		60,
		85
	};

	// --- Gestor automatico de poblacion (curva horaria + sesiones): da ritmo de vida y auto-arranque tras restart. ---
	public static volatile boolean POPULATION_AUTO = true; // Auto-arranque del gestor en el boot del GS (persistido en PhantomAI.ini, editable desde .pmenu).
	public static volatile boolean CHAT_ENABLED = true; // Habla espontanea de los phantoms (mensajes de muerte, chat ambiental). El puppeteo GM no se ve afectado.
	public static volatile boolean PK_ENABLED = true; // Permite que el rasgo PK se asigne/manifieste. Con OFF: solo duelos flageados entre phantoms; los rojos actuales se limpian solos farmeando.
	public static volatile boolean COUNT_ONLINE_WEB = true; // Fuerza online=1 en BD al loguear: sin cliente, isOnlineInt()=0 escribiria 0 y la web los mostraria offline.
	public static volatile int POPULATION_MAX = 60; // Tope absoluto de phantoms online a la vez (editable en .pmenu, persistido). OJO: maximo probado con metricas sanas = 94; subir por escalones vigilando CPU/RAM.
	public static final int POPULATION_TICK_MS = 90000; // Cada cuanto revisa el gestor.
	public static final int POPULATION_STEP = 6; // Cuantos conecta/desconecta como maximo por revision (entrada/salida gradual).
	public static final long SESSION_MIN_MS = 120L * 60000L; // Duracion minima de sesion (2h).
	public static final long SESSION_MAX_MS = 300L * 60000L; // Duracion maxima de sesion (5h).

	public static int rollPyramidLevel(int minLevel, int maxLevel)
	{
		if (minLevel > 0)
		{
			// Rango manual del GM: uniforme dentro de [min, max].
			return Rnd.get(minLevel, Math.max(minLevel, Math.min(maxLevel, PYRAMID_MAX_LEVEL)));
		}
		return rollPyramidLevel();
	}

	public static int rollPyramidLevel()
	{
		final int roll = Rnd.get(100);
		if (roll < 30)
		{
			return Rnd.get(1, 19); // 30% novatos
		}
		if (roll < 63)
		{
			return Rnd.get(20, 39); // 33% nivel medio-bajo
		}
		if (roll < 85)
		{
			return Rnd.get(40, 54); // 22% medio
		}
		if (roll < 97)
		{
			return Rnd.get(55, 69); // 12% alto
		}
		return Rnd.get(70, PYRAMID_MAX_LEVEL); // 3% veteranos
	}

	public static int targetForHour(int hour)
	{
		if (PHANTOM_IDS.isEmpty())
		{
			return 0;
		}
		final double pct = HOURLY_CURVE[Math.max(0, Math.min(3, hour / 6))] / 100.0;
		return Math.min(POPULATION_MAX, (int) Math.round(PHANTOM_IDS.size() * pct));
	}

	public static synchronized boolean toggleAutoStart()
	{
		POPULATION_AUTO = !POPULATION_AUTO;
		saveSettings();
		return POPULATION_AUTO;
	}

	public static synchronized boolean toggleChat()
	{
		CHAT_ENABLED = !CHAT_ENABLED;
		saveSettings();
		return CHAT_ENABLED;
	}

	public static synchronized boolean togglePk()
	{
		PK_ENABLED = !PK_ENABLED;
		saveSettings();
		return PK_ENABLED;
	}

	public static synchronized boolean toggleCountOnline()
	{
		COUNT_ONLINE_WEB = !COUNT_ONLINE_WEB;
		saveSettings();
		return COUNT_ONLINE_WEB;
	}

	public static synchronized void setPopulationMax(int value)
	{
		// Sin techo artificial: cada admin conoce su maquina. Referencia: ~1000 bots activos en un VPS de 12GB/4 nucleos funcionaron.
		POPULATION_MAX = Math.max(1, value);
		saveSettings();
	}

	public static synchronized void setCurve(int c1, int c2, int c3, int c4)
	{
		HOURLY_CURVE[0] = clampPct(c1);
		HOURLY_CURVE[1] = clampPct(c2);
		HOURLY_CURVE[2] = clampPct(c3);
		HOURLY_CURVE[3] = clampPct(c4);
		saveSettings();
	}

	private static int clampPct(int value)
	{
		return Math.max(0, Math.min(100, value));
	}

	public static synchronized void loadSettings()
	{
		final File file = new File(SETTINGS_PATH);
		if (!file.exists())
		{
			saveSettings(); // Primer arranque: crea el fichero con los valores por defecto.
			return;
		}
		try (FileInputStream in = new FileInputStream(file))
		{
			final Properties props = new Properties();
			props.load(in);
			for (int i = 0; i < 4; i++)
			{
				try
				{
					HOURLY_CURVE[i] = clampPct(Integer.parseInt(props.getProperty(curveKey(i), String.valueOf(HOURLY_CURVE[i])).trim()));
				}
				catch (NumberFormatException e)
				{
					// Valor corrupto: se conserva el default.
				}
			}
			POPULATION_AUTO = Boolean.parseBoolean(props.getProperty("AutoArranque", String.valueOf(POPULATION_AUTO)).trim());
			CHAT_ENABLED = Boolean.parseBoolean(props.getProperty("ChatBots", String.valueOf(CHAT_ENABLED)).trim());
			PK_ENABLED = Boolean.parseBoolean(props.getProperty("PermitirPK", String.valueOf(PK_ENABLED)).trim());
			COUNT_ONLINE_WEB = Boolean.parseBoolean(props.getProperty("ContarOnlineWeb", String.valueOf(COUNT_ONLINE_WEB)).trim());
			try
			{
				POPULATION_MAX = Math.max(1, Integer.parseInt(props.getProperty("TopeOnline", String.valueOf(POPULATION_MAX)).trim()));
			}
			catch (NumberFormatException e)
			{
				// Valor corrupto: se conserva el default.
			}
			System.out.println(">>> [PHANTOM SYSTEM] Tramos de densidad: " + HOURLY_CURVE[0] + "/" + HOURLY_CURVE[1] + "/" + HOURLY_CURVE[2] + "/" + HOURLY_CURVE[3] + "% del pool.");
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("SETTINGS", "No se pudo leer PhantomAI.ini: " + e.getMessage());
		}
	}

	private static synchronized void saveSettings()
	{
		try (FileOutputStream out = new FileOutputStream(SETTINGS_PATH))
		{
			final Properties props = new Properties();
			for (int i = 0; i < 4; i++)
			{
				props.setProperty(curveKey(i), String.valueOf(HOURLY_CURVE[i]));
			}
			props.setProperty("AutoArranque", String.valueOf(POPULATION_AUTO));
			props.setProperty("ChatBots", String.valueOf(CHAT_ENABLED));
			props.setProperty("PermitirPK", String.valueOf(PK_ENABLED));
			props.setProperty("ContarOnlineWeb", String.valueOf(COUNT_ONLINE_WEB));
			props.setProperty("TopeOnline", String.valueOf(POPULATION_MAX));
			props.store(out, "Phantom AI - tramos = % del pool online por franja horaria (0-100); AutoArranque = gestor al boot; ChatBots = habla espontanea");
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("SETTINGS", "No se pudo guardar PhantomAI.ini: " + e.getMessage());
		}
	}

	private static String curveKey(int index)
	{
		switch (index)
		{
			case 0:
			{
				return "Tramo00_06";
			}
			case 1:
			{
				return "Tramo06_12";
			}
			case 2:
			{
				return "Tramo12_18";
			}
			default:
			{
				return "Tramo18_24";
			}
		}
	}
	
	// Packs por grado con IDs VERIFICADOS contra el datapack HF: los IDs originales eran de Essence y en HF apuntaban a items distintos (magos con dos cascos y sin ropa, Tallum grado A a nivel 20, EtcItems inequipables).
	public static final int[][][] FIGHTER_GEAR_PACKS =
	{
		// 0: sin grado (1-19)
		{
			{ 1, 22, 28, 39, 42 }, // Short Sword + Leather Shirt + Pants + Boots + Leather Cap
			{ 3, 22, 28, 39, 43 }, // Broadsword + Wooden Helmet
			{ 4, 23, 28, 39, 19 }, // Club + Wooden Breastplate + Small Shield
			{ 5, 22, 28, 39, 44 } // Mace + Leather Helmet
		},
		// 1: grado D (20-39)
		{
			{ 69, 351, 417, 2411 }, // Bastard Sword + Blast Plate + Manticore Skin Gaiters + Brigandine Helmet
			{ 129, 351, 417, 2411 }, // Sword of Revolution
			{ 156, 351, 380, 2411 }, // Hand Axe + Plate Gaiters
			{ 2499, 351, 380 } // Elven Long Sword, sin casco
		},
		// 2: grado C (40-51)
		{
			{ 72, 356, 2414, 2497 }, // Stormbringer + Full Plate + FP Helmet + FP Shield
			{ 77, 356, 2414 }, // Tsurugi
			{ 135, 356, 2497 }, // Samurai Longsword
			{ 77, 354, 380, 2414 } // Chain Mail Shirt + Plate Gaiters (equipo humilde)
		},
		// 3: grado B (52-60)
		{
			{ 175, 2381, 2417 }, // Art of Battle Axe + Doom Plate + Doom Helmet
			{ 175, 2381, 2416 }, // Blue Wolf Helmet
			{ 175, 2384, 2416 }, // Zubei's Leather Shirt (look ligero)
			{ 175, 2381, 2415 } // Avadon Circlet
		},
		// 4: grado A (61-75)
		{
			{ 80, 2382 }, // Tallum Blade + Tallum Plate
			{ 81, 2382 }, // Dragon Slayer
			{ 235, 2385 }, // Bloody Orchid + Dark Crystal Leather
			{ 305, 2382 } // Tallum Glaive
		},
		// 5: grado S (76+)
		{
			{ 6580, 6373, 6374, 6378 }, // Tallum Blade*Dark Legion's Edge + Imperial Crusader
			{ 6580, 6373, 6374, 6375, 6376 } // Imperial Crusader completo
		}
	};
	public static final int[][][] MAGE_GEAR_PACKS =
	{
		// 0: sin grado (1-19)
		{
			{ 6, 425, 461 }, // Apprentice's Wand + Apprentice's Tunic + Stockings
			{ 8, 1101, 1104 }, // Willow Staff + Tunic/Stockings of Devotion
			{ 5, 425, 461, 39 } // Mace + Boots
		},
		// 1: grado D (20-39)
		{
			{ 188, 437, 470 }, // Ghost Staff + Mithril Tunic + Mithril Stockings
			{ 189, 436, 470 }, // Staff of Life + Tunic of Knowledge
			{ 90, 437, 470 } // Goat Head Staff
		},
		// 2: grado C (40-51)
		{
			{ 200, 439, 471 }, // Sage's Staff + Karmian Tunic + Karmian Stockings
			{ 84, 441, 472 }, // Homunkulus's Sword + Demon's Tunic + Demon's Stockings
			{ 6313, 439, 473 } // Homunkulus - Acumen + Divine Stockings
		},
		// 3: grado B (52-60)
		{
			{ 148, 2406, 2415 }, // Sword of Valhalla + Avadon Robe + Avadon Circlet
			{ 148, 2406 } // sin circlet
		},
		// 4: grado A (61-75)
		{
			{ 151, 2407 }, // Sword of Miracles + Dark Crystal Robe
			{ 150, 2407 }, // Elemental Sword
			{ 151, 2409 } // Majestic Robe
		},
		// 5: grado S (76+)
		{
			{ 6608, 2409 }, // Arcana Mace - Acumen + Majestic Robe
			{ 6579, 2409 } // Arcana Mace
		}
	};
	// Complementos por grado: guantes, botas, collar, pendiente, anillo (IDs verificados contra el datapack HF). La joyeria es la M.Def — sin ella una magia los funde; y manos/pies desnudos delatan.
	public static final int[][] EXTRA_GEAR =
	{
		{ 49, 39, 906, 115, 875 }, // sin grado: Gloves + Boots + Knowledge/Wisdom
		{ 2449, 2425, 913, 850, 881 }, // D: Brigandine + juego Elven
		{ 2462, 2438, 917, 853, 884 }, // C: Full Plate + Mermaid/Protection
		{ 2466, 559, 926, 864, 895 }, // B: Guardian's Gloves + Boots of Valor + juego Black Ore
		{ 2478, 563, 924, 862, 893 }, // A: Tallum Gloves + Dark Crystal Boots + juego Majestic
		{ 6375, 6376, 920, 858, 889 } // S: Imperial Crusader + juego Tateossian
	};
	// Sets de arma + torso para KAMAEL: la raza no tiene mesh grafico para armaduras heavy ONEPIECE (Full/Doom/Tallum Plate se ven como cuerpo desnudo).
	// Light armor con chest+legs SEPARADOS que si renderiza en Kamael (guantes/botas/joyeria van igual por EXTRA_GEAR, esos si tienen mesh).
	public static final int[][] KAMAEL_GEAR_PACKS =
	{
		{ 1, 22, 28 }, // sin grado: Short Sword + Leather Shirt + Pants
		{ 69, 395, 417 }, // D: Bastard Sword + Manticore Skin (light)
		{ 72, 400, 420 }, // C: Stormbringer + Theca Leather (light)
		{ 175, 2398, 2403 }, // B: Art of Battle Axe + Blue Wolf (light)
		{ 80, 2400, 2405 }, // A: Tallum Blade + Tallum Leather (light)
		{ 6580, 6373, 6374 } // S: Tallum Blade*DLE + Imperial Crusader (chest+legs separados, renderiza)
	};
	// Pociones de vida: SOLO las que venden las tiendas (verificado en buylists: 1060 en 30 tiendas, 1061 en 25; la 727 no la vende nadie).
	public static int potionForLevel(int level)
	{
		return (level < 30) ? 1060 : 1061; // Lesser / Greater Healing Potion
	}

	public static final int[] FIGHTER_SHOTS =
	{
		1835, // Soulshot: No Grade (el 1462 original era un Cristal grado S de Essence)
		1463,
		1464,
		1465,
		1466,
		1467
	};
	public static final int[] MAGE_SHOTS =
	{
		3947,
		3948,
		3949,
		3950,
		3951,
		3952
	};
	
	public static class FarmZone
	{
		public int minLvl, maxLvl, spotX, spotY, spotZ, townX, townY, townZ;
		
		public FarmZone(int min, int max, int sx, int sy, int sz, int tx, int ty, int tz)
		{
			this.minLvl = min;
			this.maxLvl = max;
			this.spotX = sx;
			this.spotY = sy;
			this.spotZ = sz;
			this.townX = tx;
			this.townY = ty;
			this.townZ = tz;
		}
	}
	
	public static void init()
	{
		ROUTES.clear();
		ROUTES.add(new FarmZone(1, 15, -75291, 251836, -3336, -84318, 244579, -3730));
		ROUTES.add(new FarmZone(15, 25, -43295, 118875, -2600, -14225, 123540, -3121));
		ROUTES.add(new FarmZone(25, 35, 33924, 134785, -2500, 15670, 142983, -2705));
		ROUTES.add(new FarmZone(35, 45, 47214, 147572, -2000, 15670, 142983, -2705));
		ROUTES.add(new FarmZone(45, 55, 138584, 9639, -3500, 146142, 26715, -2200));
		ROUTES.add(new FarmZone(55, 65, 109240, 40968, -4000, 117110, 76883, -2695));
		ROUTES.add(new FarmZone(65, 75, 140406, 12433, -3400, 146142, 26715, -2200));
		ROUTES.add(new FarmZone(75, 80, 172310, 52740, -4800, 146142, 26715, -2200));
		ROUTES.add(new FarmZone(80, 85, 110000, 118000, -2500, 83400, 147943, -3404));
		ROUTES.add(new FarmZone(85, 100, -55680, 136162, -2200, -13973, 122208, -3116));
		loadSettings();
		loadXML();
		PhantomHuntingSpots.load();
	}
	
	public static void loadXML()
	{
		PHANTOM_IDS.clear();
		try
		{
			File file = new File(PHANTOM_XML_PATH);
			if (!file.exists())
			{
				System.out.println(">>> [PHANTOM SYSTEM] ERROR: config/Custom/PhantomPlayers.xml NO ENCONTRADO.");
				return;
			}
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			NodeList list = doc.getElementsByTagName("phantom");
			for (int i = 0; i < list.getLength(); i++)
			{
				PHANTOM_IDS.add(Integer.parseInt(list.item(i).getAttributes().getNamedItem("charId").getNodeValue()));
			}
			System.out.println(">>> [PHANTOM SYSTEM] XML cargado exitosamente. " + PHANTOM_IDS.size() + " IDs.");
		}
		catch (Exception e)
		{
			System.out.println(">>> [PHANTOM SYSTEM] ERROR LEYENDO XML: " + e.getMessage());
		}
	}
	
	public static synchronized boolean addPhantomId(int charId)
	{
		if (PHANTOM_IDS.contains(charId))
		{
			return false;
		}
		
		PHANTOM_IDS.add(charId);
		saveXML();
		return true;
	}
	
	private static synchronized void saveXML()
	{
		try
		{
			File file = new File(PHANTOM_XML_PATH);
			File parent = file.getParentFile();
			if ((parent != null) && !parent.exists())
			{
				parent.mkdirs();
			}
			
			Set<Integer> uniqueIds = new LinkedHashSet<>(PHANTOM_IDS);
			try (FileWriter writer = new FileWriter(file, false))
			{
				writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
				writer.write("<list>\n");
				for (int charId : uniqueIds)
				{
					writer.write("\t<phantom charId=\"" + charId + "\" />\n");
				}
				writer.write("</list>\n");
			}
			PHANTOM_IDS.clear();
			PHANTOM_IDS.addAll(uniqueIds);
			PhantomManager.logToFile("XML", "PhantomPlayers.xml actualizado. IDs=" + PHANTOM_IDS.size());
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("XML", "No se pudo guardar PhantomPlayers.xml: " + e.getMessage());
		}
	}
	
	public static FarmZone getIdealZone(int level)
	{
		for (FarmZone zone : ROUTES)
		{
			if ((level >= zone.minLvl) && (level < zone.maxLvl))
			{
				return zone;
			}
		}
		return ROUTES.get(ROUTES.size() - 1);
	}
}
