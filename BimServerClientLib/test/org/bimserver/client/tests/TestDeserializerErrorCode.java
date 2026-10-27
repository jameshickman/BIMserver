package org.bimserver.client.tests;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.plugins.deserializers.DeserializerErrorCode;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.utils.ByteUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live-server")
public class TestDeserializerErrorCode {
	@Test
	public void test() throws Exception {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				Path path = Paths.get(System.getProperty("testFileLocation"), "TestData/data/ADT-FZK-Haus-2005-2006.ifc");
				
				try (InputStream fis = Files.newInputStream(path)) {
					byte[] initialBytes = ByteUtils.extractHead(new BufferedInputStream(fis), 4096);
					client.getServiceInterface().determineIfcVersion(initialBytes, false);
				}
			}
			Assertions.fail("Expected UserException to be thrown");
		} catch (UserException e1) {
			Assertions.assertEquals(DeserializerErrorCode.UNSUPPORTED_IFC_SCHEMA_VERSION, e1.getErrorCode());
    	}
	}
}
