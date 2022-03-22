package net.simplyrin.processmanagerplus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.simplyrin.config.Config;
import net.simplyrin.config.Configuration;
import net.simplyrin.processmanager.Callback;
import net.simplyrin.processmanager.ProcessManagerPlus;
import net.simplyrin.processmanagerplus.listener.CommandListener;
import net.simplyrin.rinstream.ChatColor;

/**
 * Created by SimplyRin on 2021/04/11.
 *
 * Copyright (C) 2021 SimplyRin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
@Getter
public class Main {

	public static void main(String[] args) {
		String customConfigName = null;
		if (args.length > 0) {
			customConfigName = args[0];
		}
		new Main().run(customConfigName);
	}

	private JDA jda;
	private Configuration config;

	private List<String> consoleMuteList = new ArrayList<>();

	private List<String> adminList = new ArrayList<>();
	private List<String> discordMuteList = new ArrayList<>();
	private long channelId;

	private ProcessManagerPlus processManagerPlus;

	private List<String> queue = new ArrayList<>();

	public void run(String customConfigName) {
		File file = new File(customConfigName != null ? customConfigName : "ProcessManagerPlus.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}

			Configuration config = Config.getConfig(file);
			config.set("WorkingDirectory", "minecraft_server");
			config.set("CloseCommand", "stop");
			config.set("ExecuteCommand", Arrays.asList("java", "-jar", "-Xms1G", "-Xmx2G", "-server", "spigot-1.16.5.jar", "nogui"));
			config.set("ConsoleMute", Arrays.asList("contains|by SpigotMC"));

			config.set("Discord.Token", "BOT_TOKEN_HERE");
			config.set("Discord.AdminList", Arrays.asList("224428706209202177"));
			config.set("Discord.MuteLine", Arrays.asList("contains|by SpigotMC", "contains|issued server command: /tps"));
			config.set("Discord.Channel-ID", 0L);
			Config.saveConfig(config, file);
		}

		this.config = Config.getConfig(file);
		List<String> executeCommand = this.config.getStringList("ExecuteCommand");
		String[] command = executeCommand.toArray(new String[executeCommand.size()]);

		this.consoleMuteList.addAll(this.config.getStringList("ConsoleMute"));

		String token = this.config.getString("Discord.Token");
		
		this.adminList.addAll(this.config.getStringList("Discord.AdminList"));
		this.discordMuteList.addAll(this.config.getStringList("Discord.MuteList"));
		
		this.channelId = this.config.getLong("Discord.Channel-ID", 0L);

		if (!token.equals("BOT_TOKEN_HERE") && this.channelId != 0L) {
			this.reconnectJDA();
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				forceSendQueue(true);
			}
		});
		this.sendQueue();

		this.processManagerPlus = new ProcessManagerPlus(command, new Callback() {
			@Override
			public void line(String response) {
				if (!isMute(consoleMuteList, response)) {
					System.out.println(response);
				}

				if (!isMute(discordMuteList, response)) {
					synchronized (queue) {
						queue.add(response);
					}
				}
			}

			@Override
			public void processEnded(int exitCode) {
				System.exit(exitCode);
			}
		});
		this.processManagerPlus.setWorkingDirectory(new File(config.getString("WorkingDirectory")));
		this.processManagerPlus.start();

		Scanner scanner = new Scanner(System.in);
		while (true) {
			String line = scanner.nextLine();
			this.processManagerPlus.sendCommand(line);

			if (line.equalsIgnoreCase(this.config.getString("CloseCommand"))) {
				break;
			}
		}

		scanner.close();
	}
	
	public void reconnectJDA() {
		try {
			if (this.jda != null) {
				this.jda.shutdownNow();
				this.jda = null;
			}
			
			List<GatewayIntent> list = new ArrayList<>();
			for (GatewayIntent intent : GatewayIntent.values()) {
				list.add(intent);
			}
			JDABuilder jdaBuilder = JDABuilder.createDefault(this.config.getString("Discord.Token"), list);
			jdaBuilder.setMemberCachePolicy(MemberCachePolicy.ALL);
			jdaBuilder.setChunkingFilter(ChunkingFilter.ALL);
			this.jda = jdaBuilder.build().awaitReady();

			this.jda.addEventListener(new CommandListener(this));
		} catch (Exception e) {
			e.printStackTrace();
			
			new Thread(() -> {
				try {
					TimeUnit.SECONDS.sleep(30);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				
				this.reconnectJDA();
			}).start();
		}
	}

	public boolean isMute(List<String> muteList, String response) {
		response = ChatColor.translate(response);
		response = ChatColor.stripColor(response);
		
		boolean isMute = false;
		for (String list : muteList) {
			String[] args = list.split("[|]");

			String type = args[0];
			String value = args[1];
			if (value.equals("%20")) {
				value = "";
			}

			if (type.equalsIgnoreCase("equals") && response.equalsIgnoreCase(value)) {
				isMute = true;
			} else if (type.equalsIgnoreCase("contains") && response.toLowerCase().contains(value.toLowerCase())) {
				isMute = true;
			}
		}

		return isMute;
	}

	public void sendCommand(String command) {
		this.processManagerPlus.sendCommand(command);
	}

	public void sendQueue() {
		new Thread() {
			@Override
			public void run() {
				while (true) {
					forceSendQueue(false);

					try {
						Thread.sleep(1000 * 3);
					} catch (Exception e) {
					}
				}
			}
		}.start();
	}

	public void forceSendQueue(boolean isComplete) {
		if (this.jda == null) {
			return;
		}
		
		MessageChannel channel = this.jda.getTextChannelById(this.channelId);
		
		String value = "";

		synchronized (this.queue) {
			boolean isSend = false;

			try {
				for (String line : this.queue) {
					if ((value + line + "\n").length() >= 2000) {
						channel.sendMessage(value).complete();
						isSend = true;
						value = "";
					}
					
					value += line + "\n";
				}
				if (!isSend && value.length() >= 1) {
					channel.sendMessage(value).complete();
				}
				this.queue.clear();
			} catch (Exception e) {
				this.reconnectJDA();
			}
		}
		
	}

}
