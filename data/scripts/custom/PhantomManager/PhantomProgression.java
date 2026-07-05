package custom.PhantomManager;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

public class PhantomProgression
{
	public static void settleClass(Player bot)
	{
		// Promociona la clase hasta que corresponda al nivel actual (una promocion por vuelta, maximo 4).
		for (int i = 0; i < 4; i++)
		{
			final PlayerClass currentClass = bot.getPlayerClass();
			final int classLevel = currentClass.level();
			final int lvl = bot.getLevel();
			final boolean needsClassChange = ((lvl >= 20) && (classLevel == 0)) || ((lvl >= 40) && (classLevel == 1)) || ((lvl >= 76) && (classLevel == 2)) || ((lvl >= 85) && (classLevel == 3));
			if (!needsClassChange)
			{
				break;
			}

			final List<PlayerClass> nextClasses = new ArrayList<>();
			for (PlayerClass cid : PlayerClass.values())
			{
				if (cid.getParent() == currentClass)
				{
					nextClasses.add(cid);
				}
			}
			if (nextClasses.isEmpty())
			{
				break;
			}
			bot.setPlayerClass(nextClasses.get(Rnd.get(nextClasses.size())).getId());
			bot.setBaseClass(bot.getPlayerClass().getId());
		}
		bot.broadcastUserInfo();
	}

	public static void checkProgression(Player bot)
	{
		int lvl = bot.getLevel();
		PlayerClass currentClass = bot.getPlayerClass();
		int classLevel = currentClass.level();
		boolean needsClassChange = ((lvl >= 20) && (classLevel == 0)) || ((lvl >= 40) && (classLevel == 1)) || ((lvl >= 76) && (classLevel == 2)) || ((lvl >= 85) && (classLevel == 3));
		
		if (needsClassChange)
		{
			List<PlayerClass> nextClasses = new ArrayList<>();
			for (PlayerClass cid : PlayerClass.values())
			{
				if (cid.getParent() == currentClass)
				{
					nextClasses.add(cid);
				}
			}
			if (!nextClasses.isEmpty())
			{
				bot.setPlayerClass(nextClasses.get(Rnd.get(nextClasses.size())).getId());
				bot.setBaseClass(bot.getPlayerClass().getId());
				bot.broadcastUserInfo();
				bot.giveAvailableSkills(false, true, false);
			}
		}

		int lastSkillLevel = PhantomState.LAST_SKILL_LEARN_LEVEL.getOrDefault(bot.getObjectId(), 0);
		if (lvl > lastSkillLevel)
		{
			PhantomState.LAST_SKILL_LEARN_LEVEL.put(bot.getObjectId(), lvl);
			// Con AutoLearnSkills=False (retail) los jugadores aprenden en el trainer; el phantom lo simula al subir de nivel.
			int learned = bot.giveAvailableSkills(false, true, false);
			if (learned > 0)
			{
				PhantomManager.logToFile(bot.getName(), "Aprende " + learned + " skills nuevas (nivel " + lvl + ").");
			}
		}

		int targetGrade = (lvl >= 76) ? 5 : (lvl >= 61) ? 4 : (lvl >= 52) ? 3 : (lvl >= 40) ? 2 : (lvl >= 20) ? 1 : 0;
		if (targetGrade > PhantomState.GEAR_GRADE.getOrDefault(bot.getObjectId(), -1))
		{
			PhantomState.GEAR_GRADE.put(bot.getObjectId(), targetGrade);
			final int[][] packs = bot.isMageClass() ? PhantomConfig.MAGE_GEAR_PACKS[targetGrade] : PhantomConfig.FIGHTER_GEAR_PACKS[targetGrade];
			final int packIndex = Rnd.get(packs.length);
			PhantomState.GEAR_PACK.put(bot.getObjectId(), packIndex);
			PhantomManager.logToFile(bot.getName(), "Equipando pack " + (bot.isMageClass() ? "mago" : "guerrero") + " grado " + targetGrade + " variante " + packIndex);
			equipPack(bot, targetGrade, packIndex);

			// Segundo pendiente/anillo solo al cambiar de grado (en reposiciones anadiria duplicados).
			final int[] extras = PhantomConfig.EXTRA_GEAR[targetGrade];
			if (Rnd.get(100) < 60)
			{
				giveAndEquip(bot, extras[3], true);
			}
			if (Rnd.get(100) < 60)
			{
				giveAndEquip(bot, extras[4], true);
			}
			bot.broadcastUserInfo();
		}
		else if ((bot.getActiveWeaponInstance() == null) || (bot.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST) == null))
		{
			// Piezas perdidas (p.ej. drop de karma al morir siendo PK): repone el pack del grado actual.
			PhantomManager.logToFile(bot.getName(), "Equipo incompleto detectado. Reponiendo pack del grado " + targetGrade + ".");
			equipPack(bot, targetGrade, PhantomState.GEAR_PACK.getOrDefault(bot.getObjectId(), 0));
			bot.broadcastUserInfo();
		}
		
		int shotId = bot.isMageClass() ? PhantomConfig.MAGE_SHOTS[targetGrade] : PhantomConfig.FIGHTER_SHOTS[targetGrade];
		if (bot.getInventory().getInventoryItemCount(shotId, -1) < 100)
		{
			bot.addItem(ItemProcessType.REWARD, shotId, 1000, bot, false);
			bot.addAutoSoulShot(shotId);
			bot.broadcastUserInfo();
		}
	}

	private static void equipPack(Player bot, int grade, int packIndex)
	{
		final int[][] packs = bot.isMageClass() ? PhantomConfig.MAGE_GEAR_PACKS[grade] : PhantomConfig.FIGHTER_GEAR_PACKS[grade];
		final int[] gearSet = packs[Math.max(0, Math.min(packIndex, packs.length - 1))];
		for (int itemId : gearSet)
		{
			giveAndEquip(bot, itemId, false);
		}
		// Complementos: guantes, botas y joyeria completa (la joyeria es la M.Def que les faltaba).
		for (int itemId : PhantomConfig.EXTRA_GEAR[grade])
		{
			giveAndEquip(bot, itemId, false);
		}
	}

	private static void giveAndEquip(Player bot, int itemId, boolean forceNew)
	{
		// forceNew: para el segundo pendiente/anillo (getItemByItemId devolveria el ya equipado).
		Item item = forceNew ? null : bot.getInventory().getItemByItemId(itemId);
		if (item == null)
		{
			item = bot.addItem(ItemProcessType.REWARD, itemId, 1L, bot, false);
		}
		if (item == null)
		{
			PhantomManager.logToFile(bot.getName(), "Item de equipo no existe o no pudo agregarse: " + itemId);
			return;
		}
		if (!item.isEquipable())
		{
			PhantomManager.logToFile(bot.getName(), "Item de equipo no equipable, omitido: " + itemId);
			return;
		}
		if (forceNew || !item.isEquipped())
		{
			bot.getInventory().equipItem(item);
		}
	}
}
