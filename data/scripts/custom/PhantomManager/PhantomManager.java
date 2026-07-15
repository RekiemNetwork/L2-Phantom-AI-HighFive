package custom.PhantomManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.l2jmobius.gameserver.handler.IVoicedCommandHandler;
import org.l2jmobius.gameserver.handler.VoicedCommandHandler;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.script.Script;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;

public class PhantomManager extends Script implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"pstart",
		"pstop",
		"pload",
		"pcreate",
		"pstop10",
		"pm",
		"pmenu",
		"pdebug",
		"ppop"
	};
	
	private static boolean _debugMode = true;
	private static String _sessionLogFile = "log/PhantomManager-session-pending.txt";
	private static int _logLinesSinceRotationCheck = 0;
	
	public PhantomManager()
	{
		// Config primero: la limpieza de logs de startLogSession debe usar DiasRetencionLogs del .ini, no el default.
		PhantomConfig.init();
		startLogSession("SERVER_START");
		VoicedCommandHandler.getInstance().registerHandler(this);
		PhantomBypass.register();
		
		System.out.println("##################################################");
		System.out.println(">>> [PHANTOM SYSTEM] V32 - Arquitectura Modular");
		System.out.println(">>> L2 Phantom AI by MiaCodeWEB (miacodeweb.com)");
		System.out.println(">>> Port a High Five: Rekiem Games Network");
		System.out.println("##################################################");

		// Auto-arranque: tras un reinicio del GS, los phantoms vuelven solos segun la curva horaria (sin .pstart manual). Desactivable desde .pmenu (persistido).
		if (PhantomConfig.POPULATION_AUTO)
		{
			PhantomPopulation.start();
		}
		else
		{
			System.out.println(">>> [PHANTOM SYSTEM] Auto-arranque DESACTIVADO (activa el gestor desde .pmenu).");
		}
	}
	
	public static void startLogSession(String reason)
	{
		_sessionLogFile = "log/PhantomManager-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
		cleanOldLogs();
		logToFile("SYSTEM", "Nueva sesion de logs: " + reason);
	}

	/**
	 * Autoborra los logs TXT de phantoms con mas de {@code PhantomConfig.LOG_RETENTION_DAYS} dias (ficheros de sesion y la generacion rotada del stream continuo).
	 */
	private static void cleanOldLogs()
	{
		try
		{
			final long cutoff = System.currentTimeMillis() - (PhantomConfig.LOG_RETENTION_DAYS * 86400000L);
			final File[] files = new File("log").listFiles((dir, name) -> (name.startsWith("PhantomManager-") && name.endsWith(".txt")) || name.equals("PhantomManager.txt.1"));
			if (files == null)
			{
				return;
			}
			int deleted = 0;
			for (File file : files)
			{
				if ((file.lastModified() < cutoff) && file.delete())
				{
					deleted++;
				}
			}
			if (deleted > 0)
			{
				logToFile("SYSTEM", "Limpieza de logs: " + deleted + " ficheros con mas de " + PhantomConfig.LOG_RETENTION_DAYS + " dias borrados.");
			}
		}
		catch (Exception e)
		{
			// La limpieza de logs nunca debe interrumpir el arranque ni una sesion.
		}
	}

	public static synchronized void logToFile(String botName, String action)
	{
		if (!_debugMode)
		{
			return;
		}
		try
		{
			// Rotacion del stream continuo: al pasar de 50 MB se renombra a .txt.1 (pisando la generacion anterior) y se empieza limpio.
			// El stat del fichero se muestrea cada 50 lineas: hacerlo por linea es un syscall extra en un metodo synchronized que a 1000 bots se nota.
			if (++_logLinesSinceRotationCheck >= 50)
			{
				_logLinesSinceRotationCheck = 0;
				final File globalLog = new File("log/PhantomManager.txt");
				if (globalLog.length() > (50L * 1024 * 1024))
				{
					final File rotated = new File("log/PhantomManager.txt.1");
					rotated.delete();
					globalLog.renameTo(rotated);
				}
			}
		}
		catch (Exception e)
		{
		}
		try (FileWriter fw = new FileWriter("log/PhantomManager.txt", true);
			PrintWriter pw = new PrintWriter(fw))
		{
			pw.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + botName + ": " + action);
		}
		catch (IOException e)
		{
		}
		try (FileWriter fw = new FileWriter(_sessionLogFile, true);
			PrintWriter pw = new PrintWriter(fw))
		{
			pw.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + botName + ": " + action);
		}
		catch (IOException e)
		{
		}
	}
	
	public static void logException(String botName, String action, Exception e)
	{
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		logToFile(botName, action + ": " + e.getMessage() + System.lineSeparator() + sw);
	}
	
	public static boolean toggleDebug()
	{
		_debugMode = !_debugMode;
		return _debugMode;
	}
	
	public static boolean isDebugMode()
	{
		return _debugMode;
	}
	
	@Override
	public String onEvent(String event, Npc npc, Player player)
	{
		if ((player == null) || !player.isGM())
		{
			return super.onEvent(event, npc, player);
		}
		
		if (event.startsWith("pgo_"))
		{
			String targetName = event.substring(4).trim();
			Player p = PhantomEngine.getPhantomByName(targetName);
			if (p != null)
			{
				player.teleToLocation(p.getLocation());
			}
			else
			{
				player.sendMessage("Phantom no encontrado.");
			}
		}
		else if (event.startsWith("pbring_"))
		{
			String targetName = event.substring(7).trim();
			Player p = PhantomEngine.getPhantomByName(targetName);
			if (p != null)
			{
				PhantomEngine.movePhantomTo(p, new org.l2jmobius.gameserver.model.Location(player.getX(), player.getY(), player.getZ(), player.getHeading()), player.getInstanceId(), "Traido por GM");
				player.sendMessage("Trajiste a " + p.getName() + ".");
			}
			else
			{
				player.sendMessage("Phantom no encontrado.");
			}
		}
		else if (event.startsWith("pkill_"))
		{
			String targetName = event.substring(6).trim();
			Player p = PhantomEngine.getPhantomByName(targetName);
			if ((p != null) && !p.isDead())
			{
				p.doDie(player);
				player.sendMessage("Has matado a " + targetName);
			}
			else
			{
				player.sendMessage("Phantom no encontrado o muerto.");
			}
		}
		else if (event.startsWith("pspawn_"))
		{
			int count = Integer.parseInt(event.substring(7));
			if ((count == 10) || (count == 50))
			{
				PhantomEngine.createAndStart(count, player);
			}
		}
		else if (event.equals("pdebug"))
		{
			player.sendMessage("Logs TXT: " + (toggleDebug() ? "ENCENDIDO" : "APAGADO"));
		}
		
		return super.onEvent(event, npc, player);
	}
	
	@Override
	public boolean onCommand(String command, Player player, String target)
	{
		if (command.equalsIgnoreCase("pstart"))
		{
			PhantomEngine.startBatch(10, player);
			return true;
		}
		else if (command.equalsIgnoreCase("pstop"))
		{
			PhantomEngine.stopSystem(player);
			return true;
		}
		else if (command.equalsIgnoreCase("pstop10"))
		{
			PhantomEngine.stopSome(10, player);
			return true;
		}
		else if (command.equalsIgnoreCase("pload"))
		{
			PhantomConfig.loadXML();
			player.sendMessage("XML de Phantoms recargado.");
			return true;
		}
		else if (command.equalsIgnoreCase("pcreate"))
		{
			int count = 10;
			if ((target != null) && !target.trim().isEmpty())
			{
				try
				{
					count = Math.max(1, Math.min(100, Integer.parseInt(target.trim())));
				}
				catch (NumberFormatException e)
				{
					player.sendMessage("Uso: .pcreate 10");
					return true;
				}
			}
			PhantomEngine.createAndStart(count, player);
			return true;
		}
		else if (command.equalsIgnoreCase("pm"))
		{
			if ((target == null) || target.trim().isEmpty())
			{
				return true;
			}
			String[] parts = target.split(" ", 2);
			if (parts.length < 2)
			{
				return true;
			}
			
			Player targetPhantom = PhantomEngine.getPhantomByName(parts[0]);
			if (targetPhantom != null)
			{
				player.sendPacket(new CreatureSay(player, ChatType.WHISPER, "->" + targetPhantom.getName(), parts[1]));
				PhantomChat.requestGemini(targetPhantom, player, parts[1], true);
			}
			else
			{
				player.sendMessage("El Phantom no esta farmeando.");
			}
			return true;
		}
		else if (command.equalsIgnoreCase("pmenu"))
		{
			PhantomMenu.showMenu(player);
			return true;
		}
		else if (command.equalsIgnoreCase("pdebug"))
		{
			player.sendMessage("Logs TXT: " + (toggleDebug() ? "ENCENDIDO" : "APAGADO"));
			return true;
		}
		else if (command.equalsIgnoreCase("ppop"))
		{
			if ("off".equalsIgnoreCase(target == null ? "" : target.trim()))
			{
				PhantomPopulation.stop();
				player.sendMessage("Gestor de poblacion: DETENIDO (control manual).");
			}
			else if ("on".equalsIgnoreCase(target == null ? "" : target.trim()))
			{
				PhantomPopulation.start();
				player.sendMessage("Gestor de poblacion: ACTIVO.");
			}
			else
			{
				final int hour = java.time.LocalTime.now().getHour();
				player.sendMessage("Poblacion: online " + PhantomEngine.activePhantoms.size() + " / objetivo " + PhantomConfig.targetForHour(hour) + " (pool " + PhantomConfig.PHANTOM_IDS.size() + ", hora " + hour + "). Uso: .ppop on|off");
			}
			return true;
		}
		return false;
	}
	
	@Override
	public String[] getCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	public static void main(String[] args)
	{
		new PhantomManager();
	}
}
