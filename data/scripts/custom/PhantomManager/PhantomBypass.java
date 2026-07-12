package custom.PhantomManager;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.handler.BypassHandler;
import org.l2jmobius.gameserver.handler.IBypassHandler;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;

public class PhantomBypass implements IBypassHandler
{
	private static final PhantomBypass INSTANCE = new PhantomBypass();
	private static final String[] COMMANDS =
	{
		"phantom_start_10",
		"phantom_stop_10",
		"phantom_stop_all",
		"phantom_reload_xml",
		"phantom_create_10",
		"phantom_create_25",
		"phantom_create_50",
		"phantom_create_100",
		"phantom_create",
		"phantom_delete_all",
		"phantom_delete_all_confirm",
		"phantom_go",
		"phantom_bring",
		"phantom_kill",
		"phantom_logout",
		"phantom_pm",
		"phantom_say",
		"phantom_pop_on",
		"phantom_pop_off",
		"phantom_curve",
		"phantom_maxpop",
		"phantom_chat_toggle",
		"phantom_autostart_toggle",
		"phantom_pk_toggle",
		"phantom_online_toggle",
		"phantom_list",
		"phantom_debug",
		"phantom_menu"
	};
	private static boolean _registered = false;

	public static void register()
	{
		if (!_registered)
		{
			BypassHandler.getInstance().registerHandler(INSTANCE);
			_registered = true;
		}
	}

	@Override
	public boolean onCommand(String command, Player player, Creature bypassOrigin)
	{
		if ((player == null) || !player.isGM())
		{
			return false;
		}

		// Los lotes fijos crean todo a nivel 1: para niveles variados esta el boton de piramide.
		if (command.equals("phantom_create_10"))
		{
			PhantomEngine.createAndStart(10, 1, 1, player);
			return true;
		}
		else if (command.equals("phantom_create_25"))
		{
			PhantomEngine.createAndStart(25, 1, 1, player);
			return true;
		}
		else if (command.equals("phantom_create_50"))
		{
			PhantomEngine.createAndStart(50, 1, 1, player);
			return true;
		}
		else if (command.equals("phantom_create_100"))
		{
			PhantomEngine.createAndStart(100, 1, 1, player);
			return true;
		}
		else if (command.equals("phantom_delete_all"))
		{
			PhantomMenu.showDeleteConfirm(player);
			return true;
		}
		else if (command.equals("phantom_delete_all_confirm"))
		{
			PhantomEngine.deleteAllPhantoms(player);
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.startsWith("phantom_create "))
		{
			try
			{
				final String[] parts = command.substring(15).trim().split("\\s+");
				final int count = Math.max(1, Math.min(100, Integer.parseInt(parts[0])));
				if (parts.length >= 3)
				{
					int minLevel = Math.max(1, Math.min(PhantomConfig.PYRAMID_MAX_LEVEL, Integer.parseInt(parts[1])));
					int maxLevel = Math.max(1, Math.min(PhantomConfig.PYRAMID_MAX_LEVEL, Integer.parseInt(parts[2])));
					if (minLevel > maxLevel)
					{
						final int swap = minLevel;
						minLevel = maxLevel;
						maxLevel = swap;
					}
					PhantomEngine.createAndStart(count, minLevel, maxLevel, player);
				}
				else if (parts.length == 2)
				{
					player.sendMessage("Rellena nivel min Y max (o ninguno para piramide).");
					PhantomMenu.showMenu(player);
				}
				else
				{
					PhantomEngine.createAndStart(count, player);
				}
			}
			catch (NumberFormatException e)
			{
				player.sendMessage("Cantidad o niveles no validos.");
				PhantomMenu.showMenu(player);
			}
			return true;
		}
		else if (command.startsWith("phantom_curve "))
		{
			try
			{
				final String[] parts = command.substring(14).trim().split("\\s+");
				if (parts.length != 4)
				{
					player.sendMessage("Rellena los 4 tramos (0-100).");
				}
				else
				{
					PhantomConfig.setCurve(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
					player.sendMessage("Tramos de densidad guardados.");
				}
			}
			catch (NumberFormatException e)
			{
				player.sendMessage("Tramos no validos (usa numeros 0-100).");
			}
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_start_10"))
		{
			PhantomEngine.startBatch(10, player);
			return true;
		}
		else if (command.equals("phantom_stop_10"))
		{
			PhantomEngine.stopSome(10, player);
			return true;
		}
		else if (command.equals("phantom_stop_all"))
		{
			PhantomEngine.stopSystem(player);
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_reload_xml"))
		{
			PhantomConfig.loadXML();
			player.sendMessage("XML de Phantoms recargado.");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_pop_on"))
		{
			PhantomPopulation.start();
			player.sendMessage("Gestor de poblacion: ACTIVO.");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_pop_off"))
		{
			PhantomPopulation.stop();
			player.sendMessage("Gestor de poblacion: PARADO (control manual).");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.startsWith("phantom_list"))
		{
			int page = 1;
			try
			{
				page = Integer.parseInt(command.substring(12).trim());
			}
			catch (Exception e)
			{
				// Sin numero de pagina: primera.
			}
			PhantomHtml.showList(player, page);
			return true;
		}
		else if (command.startsWith("phantom_go "))
		{
			final Player target = PhantomEngine.getPhantomByName(command.substring(11).trim());
			if (target != null)
			{
				player.teleToLocation(target.getLocation());
			}
			return true;
		}
		else if (command.startsWith("phantom_bring "))
		{
			final Player target = PhantomEngine.getPhantomByName(command.substring(14).trim());
			if (target != null)
			{
				PhantomEngine.movePhantomTo(target, new Location(player.getX(), player.getY(), player.getZ(), player.getHeading()), player.getInstanceId(), "Traido por GM");
				player.sendMessage("Trajiste a " + target.getName() + ".");
			}
			else
			{
				player.sendMessage("Phantom no encontrado.");
			}
			PhantomHtml.showList(player, 1);
			return true;
		}
		else if (command.startsWith("phantom_kill "))
		{
			final Player target = PhantomEngine.getPhantomByName(command.substring(13).trim());
			if ((target != null) && !target.isDead())
			{
				target.doDie(player);
			}
			PhantomHtml.showList(player, 1);
			return true;
		}
		else if (command.startsWith("phantom_logout "))
		{
			final Player target = PhantomEngine.getPhantomByName(command.substring(15).trim());
			if (target != null)
			{
				PhantomEngine.logoutPhantom(target);
				player.sendMessage(target.getName() + " desconectado.");
			}
			PhantomHtml.showList(player, 1);
			return true;
		}
		else if (command.startsWith("phantom_say "))
		{
			final String[] parts = command.substring(12).trim().split(" ", 2);
			if (parts.length < 2)
			{
				player.sendMessage("Uso: nombre + texto.");
				PhantomMenu.showMenu(player);
				return true;
			}
			final Player target = PhantomEngine.getPhantomByName(parts[0]);
			if (target != null)
			{
				// Retraso de tecleo simulado: que no hable en el mismo instante en que el GM pulsa el boton.
				final String text = parts[1];
				ThreadPool.schedule(() -> PhantomChat.botReply(target, null, text, false), 1500 + (text.length() * Rnd.get(60, 110)));
				player.sendMessage(target.getName() + " lo dira en unos segundos.");
			}
			else
			{
				player.sendMessage("El phantom no esta online.");
			}
			return true;
		}
		else if (command.startsWith("phantom_pm "))
		{
			final String[] parts = command.substring(11).trim().split(" ", 2);
			if (parts.length < 2)
			{
				player.sendMessage("Uso: nombre + texto.");
				PhantomMenu.showMenu(player);
				return true;
			}
			final Player target = PhantomEngine.getPhantomByName(parts[0]);
			if (target != null)
			{
				player.sendPacket(new CreatureSay(player, ChatType.WHISPER, "->" + target.getName(), parts[1]));
				PhantomChat.requestGemini(target, player, parts[1], true);
			}
			else
			{
				player.sendMessage("El phantom no esta online.");
			}
			return true;
		}
		else if (command.equals("phantom_chat_toggle"))
		{
			player.sendMessage("Chat espontaneo de bots: " + (PhantomConfig.toggleChat() ? "ACTIVADO" : "DESACTIVADO") + ".");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_autostart_toggle"))
		{
			player.sendMessage("Auto-arranque tras reinicio: " + (PhantomConfig.toggleAutoStart() ? "ACTIVADO" : "DESACTIVADO") + ".");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.startsWith("phantom_maxpop "))
		{
			try
			{
				final int value = Integer.parseInt(command.substring(15).trim());
				PhantomConfig.setPopulationMax(value);
				player.sendMessage("Tope de bots online: " + PhantomConfig.POPULATION_MAX + ". (Vigila CPU/RAM al subir por escalones.)");
			}
			catch (NumberFormatException e)
			{
				player.sendMessage("Tope no valido.");
			}
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_pk_toggle"))
		{
			player.sendMessage("Modo PK de bots: " + (PhantomConfig.togglePk() ? "PERMITIDO" : "DESACTIVADO") + " (los rojos actuales se limpian solos farmeando).");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_online_toggle"))
		{
			final boolean value = PhantomConfig.toggleCountOnline();
			PhantomEngine.applyOnlineFlagToAll(value);
			player.sendMessage("Contar como online en la web: " + (value ? "SI" : "NO") + " (aplicado a los " + PhantomEngine.activePhantoms.size() + " activos).");
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_debug"))
		{
			player.sendMessage("Logs TXT: " + (PhantomManager.toggleDebug() ? "ENCENDIDO" : "APAGADO"));
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equals("phantom_menu"))
		{
			PhantomMenu.showMenu(player);
			return true;
		}
		return false;
	}

	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
