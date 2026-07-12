package custom.PhantomManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;

public class PhantomFactory
{
	private static final String ACCOUNT_NAME = "phantom_ai";
	private static final PlayerClass[] BASE_CLASSES =
	{
		PlayerClass.FIGHTER,
		PlayerClass.MAGE,
		PlayerClass.ELVEN_FIGHTER,
		PlayerClass.ELVEN_MAGE,
		PlayerClass.DARK_FIGHTER,
		PlayerClass.DARK_MAGE,
		PlayerClass.ORC_FIGHTER,
		PlayerClass.ORC_MAGE,
		PlayerClass.DWARVEN_FIGHTER,
		PlayerClass.MALE_SOLDIER,
		PlayerClass.FEMALE_SOLDIER
	};
	private static final String[] FIRST_NAMES =
	{
		"Adrian",
		"Bruno",
		"Camila",
		"Daniel",
		"Elena",
		"Fabian",
		"Gabriel",
		"Hector",
		"Ivan",
		"Julian",
		"Laura",
		"Lucas",
		"Marcos",
		"Matias",
		"Nicolas",
		"Paula",
		"Rafael",
		"Sofia",
		"Tomas",
		"Valeria"
	};
	private static final String[] SYL_START =
	{
		"Ka",
		"Ael",
		"Va",
		"Ther",
		"Mor",
		"El",
		"Sha",
		"Zan",
		"Ry",
		"Ni",
		"Dra",
		"Fen",
		"Lu",
		"Ash",
		"Ky",
		"Sel",
		"Tor",
		"Ver",
		"Ori",
		"Bel"
	};
	private static final String[] SYL_MID =
	{
		"ra",
		"li",
		"an",
		"dor",
		"ven",
		"na",
		"mir",
		"ka",
		"the",
		"ris",
		"lo",
		"du",
		"sha",
		"el",
		"va"
	};
	private static final String[] SYL_END =
	{
		"s",
		"n",
		"r",
		"th",
		"x",
		"dor",
		"mar",
		"nis",
		"las",
		"ion",
		"ria",
		"wyn",
		"iel",
		"or",
		"us"
	};
	private static final String[] LAST_PARTS =
	{
		"Stone",
		"Blade",
		"River",
		"Storm",
		"Cross",
		"Vale",
		"Moon",
		"Steel",
		"Raven",
		"Frost",
		"Light",
		"Shade"
	};
	
	public static List<Player> createPhantoms(int count)
	{
		return createPhantoms(count, 0, 0);
	}

	public static List<Player> createPhantoms(int count, int minLevel, int maxLevel)
	{
		List<Player> created = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			Player phantom = createOne(minLevel, maxLevel);
			if (phantom != null)
			{
				created.add(phantom);
			}
		}
		return created;
	}
	
	/**
	 * Devuelve todos los charId de la cuenta de phantoms leyendo directamente la BD.
	 * Incluye huerfanos que ya no esten en el pool XML.
	 * @return lista de charId de la cuenta de phantoms
	 */
	public static List<Integer> getAllPhantomCharIds()
	{
		final List<Integer> charIds = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT charId FROM characters WHERE account_name=?"))
		{
			ps.setString(1, ACCOUNT_NAME);
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					charIds.add(rs.getInt(1));
				}
			}
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("DELETE_ALL", "Error consultando la cuenta " + ACCOUNT_NAME + ": " + e.getMessage());
		}
		return charIds;
	}

	private static Player createOne(int minLevel, int maxLevel)
	{
		try
		{
			PlayerClass playerClass = getRandomAvailableBaseClass();
			if (playerClass == null)
			{
				return null;
			}
			
			PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(playerClass);
			String name = buildUniqueName();
			boolean female = Rnd.get(100) < 45;
			if (playerClass == PlayerClass.MALE_SOLDIER)
			{
				female = false; // Kamael base classes are gender locked.
			}
			else if (playerClass == PlayerClass.FEMALE_SOLDIER)
			{
				female = true;
			}
			// Rangos validos de H5 (los mismos que valida CharacterCreate): pelo M 0-4 / F 0-6, color 0-3, cara 0-2. Fuera de rango el cliente renderiza SIN pelo.
			byte hairStyle = (byte) Rnd.get(female ? 7 : 5);
			byte hairColor = (byte) Rnd.get(4);
			byte face = (byte) Rnd.get(3);
			
			Player phantom = Player.create(template, ACCOUNT_NAME, name, new PlayerAppearance(face, hairColor, hairStyle, female));
			if (phantom == null)
			{
				return null;
			}
			
			phantom.setOnlineStatus(true, true);
			phantom.setRunning();

			// Piramide de niveles (o rango manual del GM): reparte la poblacion por todo el mapa en vez de una guarderia a nivel 1.
			final int targetLevel = PhantomConfig.rollPyramidLevel(minLevel, maxLevel);
			if (targetLevel > 1)
			{
				phantom.getStat().setExp(ExperienceData.getInstance().getExpForLevel(targetLevel));
				phantom.getStat().setLevel((byte) targetLevel);
				PhantomProgression.settleClass(phantom); // Promociona la cadena de clases para que un nivel 45 no sea clase base.
				phantom.giveAvailableSkills(false, true, false);
			}

			phantom.setCurrentHp(phantom.getMaxHp());
			phantom.setCurrentMp(phantom.getMaxMp());
			phantom.storeMe();
			PhantomManager.logToFile(name, "Creado automaticamente. Raza: " + template.getRace() + " Nivel: " + targetLevel);
			return phantom;
		}
		catch (Exception e)
		{
			PhantomManager.logToFile("AUTO_CREATE", "Error creando phantom: " + e.getMessage());
			return null;
		}
	}
	
	private static PlayerClass getRandomAvailableBaseClass()
	{
		List<PlayerClass> available = new ArrayList<>();
		for (PlayerClass playerClass : BASE_CLASSES)
		{
			PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(playerClass);
			if ((template != null) && isPlayableRace(template.getRace()))
			{
				available.add(playerClass);
			}
		}
		return available.isEmpty() ? null : available.get(Rnd.get(available.size()));
	}
	
	private static boolean isPlayableRace(Race race)
	{
		return (race == Race.HUMAN) || (race == Race.ELF) || (race == Race.DARK_ELF) || (race == Race.ORC) || (race == Race.DWARF) || (race == Race.KAMAEL);
	}
	
	private static String buildUniqueName()
	{
		for (int i = 0; i < 100; i++)
		{
			String name = buildRandomName();
			if (!CharInfoTable.getInstance().doesCharNameExist(name))
			{
				return name;
			}
		}
		return "Phantom" + (System.currentTimeMillis() % 100000000L);
	}

	private static String buildRandomName()
	{
		// Mezcla de estilos para que los nombres no sigan un unico patron reconocible.
		String name = SYL_START[Rnd.get(SYL_START.length)] + SYL_MID[Rnd.get(SYL_MID.length)] + ((Rnd.get(100) < 30) ? SYL_MID[Rnd.get(SYL_MID.length)] : "") + SYL_END[Rnd.get(SYL_END.length)];
		switch (Rnd.get(10))
		{
			case 0:
			case 1:
			{
				name = FIRST_NAMES[Rnd.get(FIRST_NAMES.length)] + LAST_PARTS[Rnd.get(LAST_PARTS.length)];
				break;
			}
			case 2:
			{
				name = name.toLowerCase();
				break;
			}
			case 3:
			{
				name = name + Rnd.get(2, 99);
				break;
			}
			case 4:
			{
				name = "x" + name;
				break;
			}
			default:
			{
				break; // Fantasia capitalizada tal cual.
			}
		}
		return (name.length() > 16) ? name.substring(0, 16) : name;
	}
}
