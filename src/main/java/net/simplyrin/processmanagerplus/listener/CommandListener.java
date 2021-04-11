package net.simplyrin.processmanagerplus.listener;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.simplyrin.processmanagerplus.Main;

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
@RequiredArgsConstructor
public class CommandListener extends ListenerAdapter {

	private final Main instance;

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (event.isFromType(ChannelType.PRIVATE)) {
			return;
		}

		User user = event.getAuthor();
		if (user.isBot()) {
			return;
		}

		MessageChannel channel = event.getChannel();
		long channelId = channel.getIdLong();

		if (this.instance.getChannelId() != channelId) {
			return;
		}

		if (!this.instance.getAdminList().contains(user.getId())) {
			return;
		}

		this.instance.sendCommand(event.getMessage().getContentRaw());
	}

}
