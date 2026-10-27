package org.bimserver.tests;

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

import org.bimserver.BimServer;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.bimserver.shared.BimServerClientFactory;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

public class TestWithEmbeddedServer {

	@BeforeAll
	public static void beforeAll() {  // TODO remove? I don't think this overrides anything
	}
	
	public BimServer getBimServer() {
		return AllTests.getBimServer();
	}
	
	public BimServerClientFactory getFactory() {
		return AllTests.getFactory();
	}
	
	/**
	 * The suite shares one server, so undo server-wide settings that individual tests change.
	 */
	@AfterEach
	public void restoreServerSettings() throws Exception {
		try (BimServerClientInterface client = getFactory().create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
			client.getSettingsInterface().setGenerateGeometryOnCheckin(true);
		}
	}

	@AfterAll
	public static void afterAll() {
		if (!AllTests.running) {
			AllTests.resetBimServer();
		}
	}
}