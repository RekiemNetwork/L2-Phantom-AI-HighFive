package custom.PhantomManager;

import java.util.List;

import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.data.sql.CharSummonTable;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;

public class PhantomEquipment
{
	private static final int INVENTORY_CLEAN_LIMIT = 18;
	
	public static void checkProgression(Player bot)
	{
		PhantomProgression.checkProgression(bot);
		ensureShots(bot);
		ensurePotions(bot);
		ensureSummon(bot);
	}

	private static void ensureSummon(Player bot)
	{
		if ((bot.getSummon() != null) || bot.isDead() || bot.isAttackingNow() || bot.isCastingNow() || bot.isMoving() || (bot.getCurrentMpPercent() < 40))
		{
			return;
		}
		final long now = System.currentTimeMillis();
		if ((now - PhantomState.LAST_SUMMON_TRY.getOrDefault(bot.getObjectId(), 0L)) < 60000)
		{
			return;
		}
		PhantomState.LAST_SUMMON_TRY.put(bot.getObjectId(), now);
		final Skill summonSkill = findServitorSkill(bot);
		if (summonSkill == null)
		{
			return;
		}
		// El casteo dura hasta 15s y es interrumpible: no intentarlo con mobs encima.
		for (Monster m : World.getInstance().getVisibleObjectsInRange(bot, Monster.class, 700))
		{
			if (!m.isDead() && (m.getTarget() == bot))
			{
				return;
			}
		}
		// A niveles 1-4 la skill tiene 90 MIN de reuse que se fija al INICIAR el casteo: una interrupcion la dejaria bloqueada hora y media.
		if (bot.isSkillDisabled(summonSkill))
		{
			bot.enableSkill(summonSkill);
		}
		// Los servitors consumen Spirit Ore al invocar y como mantenimiento periodico.
		if (bot.getInventory().getInventoryItemCount(3031, -1) < 50)
		{
			bot.addItem(ItemProcessType.REWARD, 3031, 300, bot, false);
		}
		// Algunos summons (golems de Warsmith) consumen ademas cristales declarados en la propia skill: aprovisionar lo que pida.
		if ((summonSkill.getItemConsumeId() > 0) && (bot.getInventory().getInventoryItemCount(summonSkill.getItemConsumeId(), -1) < (summonSkill.getItemConsumeCount() * 20L)))
		{
			bot.addItem(ItemProcessType.REWARD, summonSkill.getItemConsumeId(), Math.max(100, summonSkill.getItemConsumeCount() * 50), bot, false);
		}
		PhantomManager.logToFile(bot.getName(), "Invoca servitor: " + summonSkill.getName());
		bot.setTarget(bot);
		bot.doCast(summonSkill);
	}

	private static Skill findServitorSkill(Player bot)
	{
		for (Skill skill : bot.getAllSkills())
		{
			if ((skill == null) || skill.isPassive())
			{
				continue;
			}
			// Filtro por el EFECTO real (clase "Summon" = crea servitor), no por el nombre: "Summon Treasure Key"/"Siege Golem"/cubics NO son servitors de combate.
			final String name = skill.getName().toLowerCase();
			if (name.contains("cubic") || name.contains("siege golem") || name.contains("treasure") || name.contains("key"))
			{
				continue;
			}
			// getEffects devuelve NULL (no lista vacia) para skills sin efectos en este scope.
			final List<AbstractEffect> effects = skill.getEffects(EffectScope.GENERAL);
			if (effects == null)
			{
				continue;
			}
			for (AbstractEffect effect : effects)
			{
				if ((effect != null) && "Summon".equals(effect.getClass().getSimpleName()))
				{
					return skill;
				}
			}
		}
		return null;
	}

	private static void ensurePotions(Player bot)
	{
		final int potionId = PhantomConfig.potionForLevel(bot.getLevel());
		if (bot.getInventory().getInventoryItemCount(potionId, -1) < 5)
		{
			bot.addItem(ItemProcessType.REWARD, potionId, Rnd.get(10, 20), bot, false);
			PhantomManager.logToFile(bot.getName(), "Repone pociones de vida (" + potionId + ").");
		}
	}
	
	public static void cleanInventory(Player bot)
	{
		if (bot.getInventory().getSize() > INVENTORY_CLEAN_LIMIT)
		{
			int removed = 0;
			for (Item item : bot.getInventory().getItems())
			{
				if (!item.isEquipped() && (item.getId() != 57) && (item.getId() != 3031) && ((item.getId() < 1458) || (item.getId() > 1462)) && !isShot(item.getId()) && !isPotion(item.getId()))
				{
					bot.destroyItem(ItemProcessType.DESTROY, item, bot, false);
					removed++;
				}
			}
			if (removed > 0)
			{
				PhantomManager.logToFile(bot.getName(), "Inventario limpiado automaticamente. Items removidos: " + removed);
			}
			bot.broadcastUserInfo();
		}
	}
	
	private static void ensureShots(Player bot)
	{
		int grade = PhantomState.GEAR_GRADE.getOrDefault(bot.getObjectId(), 0);
		if (grade < 0)
		{
			grade = 0;
		}
		if (grade >= PhantomConfig.FIGHTER_SHOTS.length)
		{
			grade = PhantomConfig.FIGHTER_SHOTS.length - 1;
		}
		
		int primaryShot = bot.isMageClass() ? PhantomConfig.MAGE_SHOTS[grade] : PhantomConfig.FIGHTER_SHOTS[grade];
		int secondaryShot = bot.isMageClass() ? PhantomConfig.FIGHTER_SHOTS[grade] : PhantomConfig.MAGE_SHOTS[grade];
		reloadShot(bot, primaryShot, Rnd.get(1800, 3600), true);
		if (Rnd.get(100) < 35)
		{
			reloadShot(bot, secondaryShot, Rnd.get(300, 900), false);
		}
	}
	
	private static void reloadShot(Player bot, int shotId, long amount, boolean autoUse)
	{
		if (shotId <= 0)
		{
			return;
		}
		if (bot.getInventory().getInventoryItemCount(shotId, -1) >= 250)
		{
			if (autoUse)
			{
				bot.addAutoSoulShot(shotId);
			}
			return;
		}
		
		bot.addItem(ItemProcessType.REWARD, shotId, amount, bot, false);
		bot.addAutoSoulShot(shotId);
		PhantomManager.logToFile(bot.getName(), "Recarga shots itemId=" + shotId + " cantidad=" + amount);
	}
	
	public static void applyBasicBuffs(Player bot)
	{
		// Sin buffs regalados: un phantom de nivel bajo con Haste/WW sin buffer NPC es imposible y delata. Solo self-buffs reales de su clase.
		applyKnownSelfBuffs(bot);
	}
	
	private static void applyKnownSelfBuffs(Player bot)
	{
		// Un doCast pisa cualquier casteo en curso: sin este guard, el buff abortaba el summon de 15s una y otra vez.
		if (bot.isCastingNow() || bot.isAttackingNow())
		{
			return;
		}
		int objectId = bot.getObjectId();
		long now = System.currentTimeMillis();
		if ((now - PhantomState.LAST_SELF_BUFF.getOrDefault(objectId, 0L)) < 60000L)
		{
			return;
		}
		if ((bot.getCurrentMpPercent() < 35) || (bot.getEffectList().getBuffCount() >= 8))
		{
			return;
		}
		
		for (Skill skill : bot.getAllSkills())
		{
			if ((skill == null) || skill.isPassive() || skill.hasNegativeEffect())
			{
				continue;
			}
			
			String name = skill.getName().toLowerCase();
			if (name.contains("shield") || name.contains("might") || name.contains("focus") || name.contains("haste") || name.contains("empower") || name.contains("acumen") || name.contains("wind walk") || name.contains("concentration") || name.contains("vampiric") || name.contains("blessing") || name.contains("chant") || name.contains("song") || name.contains("dance"))
			{
				// Casteo real (animacion + gasto de MP) en vez de applyEffects: un buff que aparece de la nada delata al phantom.
				bot.setTarget(bot);
				bot.doCast(skill);
				PhantomState.LAST_SELF_BUFF.put(objectId, now + Rnd.get(15000, 45000));
				PhantomManager.logToFile(bot.getName(), "Usa buff propio: " + skill.getName());
				return;
			}
		}
	}
	
	static boolean isPotion(int itemId)
	{
		return (itemId == 1060) || (itemId == 1061);
	}

	static boolean isShot(int itemId)
	{
		for (int id : PhantomConfig.FIGHTER_SHOTS)
		{
			if (id == itemId)
			{
				return true;
			}
		}
		for (int id : PhantomConfig.MAGE_SHOTS)
		{
			if (id == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
