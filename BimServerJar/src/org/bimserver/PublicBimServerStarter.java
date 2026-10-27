package org.bimserver;

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.bimserver.interfaces.objects.SWebModulePluginConfiguration;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.shared.LocalDevelopmentResourceFetcher;
import org.bimserver.shared.interfaces.AdminInterface;
import org.bimserver.shared.interfaces.PluginInterface;
import org.bimserver.shared.interfaces.SettingsInterface;
import org.bimserver.webservices.authorization.SystemAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a development BIMserver that will be reachable from the internet (e.g. through an ngrok tunnel), so unlike
 * {@link LocalDevBimServerStarter} it never creates the default admin/admin account. Configured through environment
 * variables, see ngrok/README.md.
 */
public class PublicBimServerStarter extends AbstractLocalDevBimServerStarter {
	private static final Logger LOGGER = LoggerFactory.getLogger(PublicBimServerStarter.class);
	private static final int MIN_PASSWORD_LENGTH = 16;
	private static final String[] PLUGIN_BUNDLES = { "ifcplugins", "binaryserializers", "ifcopenshellplugin", "mergers", "bimviews" };
	private static final String DEFAULT_WEB_MODULE = "BIMvie.ws";

	private final String adminUsername;
	private final String adminPassword;
	private final String siteAddress;

	public PublicBimServerStarter(String adminUsername, String adminPassword, String siteAddress) {
		this.adminUsername = adminUsername;
		this.adminPassword = adminPassword;
		this.siteAddress = siteAddress;
	}

	public static void main(String[] args) {
		int port = Integer.parseInt(env("BIMSERVER_PORT", "8080"));
		String adminUsername = env("BIMSERVER_ADMIN_USERNAME", "admin@bimserver.org");
		String adminPassword = env("BIMSERVER_ADMIN_PASSWORD", null);
		String siteAddress = env("BIMSERVER_SITE_ADDRESS", "http://localhost:" + port);
		Path home = Paths.get(env("BIMSERVER_HOME", "home-public"));

		String problem = checkPassword(adminPassword, adminUsername);
		if (problem != null) {
			System.err.println("Refusing to start a publicly reachable BIMserver: " + problem);
			System.exit(2);
		}

		new PublicBimServerStarter(adminUsername, adminPassword, siteAddress).start(-1, "127.0.0.1", env("BIMSERVER_NAME", "BIMserver"), port, port + 5, new Path[0], home,
				new LocalDevelopmentResourceFetcher(Paths.get("../")), Paths.get("../BimServer").resolve("www").toAbsolutePath().toString(), true);
	}

	static String checkPassword(String password, String username) {
		if (password == null || password.isEmpty()) {
			return "BIMSERVER_ADMIN_PASSWORD is not set";
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			return "BIMSERVER_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters";
		}
		if (password.equalsIgnoreCase("admin") || password.equalsIgnoreCase(username)) {
			return "BIMSERVER_ADMIN_PASSWORD is too easy to guess";
		}
		return null;
	}

	@Override
	protected void autoSetup(BimServer bimServer, int port, String name) {
		try {
			SystemAuthorization authorization = new SystemAuthorization(1, TimeUnit.HOURS);
			AdminInterface adminInterface = bimServer.getServiceFactory().get(authorization, AccessMethod.INTERNAL).get(AdminInterface.class);
			adminInterface.setup(siteAddress, name, "BIMserver", siteAddress + "/img/bimserver.png", "Administrator", adminUsername, adminPassword);
			SettingsInterface settingsInterface = bimServer.getServiceFactory().get(authorization, AccessMethod.INTERNAL).get(SettingsInterface.class);
			settingsInterface.setCacheOutputFiles(false);
			settingsInterface.setPluginStrictVersionChecking(false);

			PluginInterface pluginInterface = bimServer.getServiceFactory().get(authorization, AccessMethod.INTERNAL).get(PluginInterface.class);
			for (String bundle : PLUGIN_BUNDLES) {
				LOGGER.info("Installing plugin bundle " + bundle);
				pluginInterface.installPluginBundle("https://repo1.maven.org/maven2/", "org.opensourcebim", bundle, null, null);
			}
			setDefaultWebModule(pluginInterface);
		} catch (Exception e) {
			// A server left in the "not set up" state lets anyone who reaches it create the admin account
			LOGGER.error("Setup failed, stopping", e);
			System.exit(1);
		}
	}

	@Override
	protected void afterStart(BimServer bimServer) {
		// The saved default web module is not reliably restored when the server starts, so apply it again
		try {
			setDefaultWebModule(bimServer.getServiceFactory().get(new SystemAuthorization(1, TimeUnit.HOURS), AccessMethod.INTERNAL).get(PluginInterface.class));
		} catch (Exception e) {
			LOGGER.error("Could not set the default web module", e);
		}
	}

	/**
	 * The server's own start page only shows its status; the web app provides the login.
	 */
	private static void setDefaultWebModule(PluginInterface pluginInterface) throws Exception {
		for (SWebModulePluginConfiguration webModule : pluginInterface.listAllWebModules()) {
			if (webModule.getName().equals(DEFAULT_WEB_MODULE)) {
				pluginInterface.setDefaultWebModule(webModule.getOid());
			}
		}
	}

	private static String env(String name, String defaultValue) {
		String value = System.getenv(name);
		return value == null || value.isEmpty() ? defaultValue : value;
	}
}
