package net.simplyrin.processmanagerplus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.simplyrin.config.Config;
import net.simplyrin.config.Configuration;
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

	private PtyProcess process;
	private OutputStream outputStream;

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
			try {
				List<GatewayIntent> list = new ArrayList<>();
				for (GatewayIntent intent : GatewayIntent.values()) {
					list.add(intent);
				}
				JDABuilder jdaBuilder = JDABuilder.createDefault(token, list);
				jdaBuilder.setMemberCachePolicy(MemberCachePolicy.ALL);
				jdaBuilder.setChunkingFilter(ChunkingFilter.ALL);
				this.jda = jdaBuilder.build().awaitReady();

				this.jda.addEventListener(new CommandListener(this));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				forceSendQueue();
			}
		});
		this.sendQueue();
		
		PtyProcessBuilder builder = new PtyProcessBuilder(command)
				.setDirectory(config.getString("WorkingDirectory"))
				.setWindowsAnsiColorEnabled(true);
		try {
			this.process = builder.start();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		this.outputStream = this.process.getOutputStream();
		
		new Thread(() -> {
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(this.process.getInputStream()));
			String response = null;
			try {
				while ((response = bufferedReader.readLine()) != null) {
					if (!this.isMute(this.consoleMuteList, response)) {
						System.out.println(response);
					}

					if (!this.isMute(this.discordMuteList, response)) {
						synchronized (this.queue) {
							this.queue.add(response);
						}
					}
				}
			} catch (Exception e) {
			}
		}).start();
		
		Runnable runnable = () -> {
			int exitCode = 0;
			try {
				exitCode = this.process.waitFor();
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.exit(exitCode);
		};
		new Thread(runnable).start();

		Scanner scanner = new Scanner(System.in);
		while (true) {
			String line = scanner.nextLine();
			this.sendCommand(line);

			if (line.equalsIgnoreCase(this.config.getString("CloseCommand"))) {
				break;
			}
		}

		scanner.close();
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
		var output = new OutputStreamWriter(this.outputStream);
		try {
			output.write(command + "\n");
			output.flush();
		} catch (Exception e) {
		}
	}

	public void sendQueue() {
		new Thread() {
			@Override
			public void run() {
				while (true) {
					forceSendQueue();

					try {
						Thread.sleep(1000 * 3);
					} catch (Exception e) {
					}
				}
			}
		}.start();
	}

	public void forceSendQueue() {
		MessageChannel channel = this.jda.getTextChannelById(this.channelId);
		
		String value = "";

		synchronized (this.queue) {
			boolean isSend = false;
			for (String line : this.queue) {
				if ((value + line + "\n").length() > 2000) {
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
		}
		
	}

}
