package org.bimserver.client.tests;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.ClientIfcModel;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SLongCheckinActionState;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.ifc4.IfcColourRgbList;
import org.bimserver.models.ifc4.IfcNormalisedRatioMeasure;
import org.bimserver.models.ifc4.ListOfIfcNormalisedRatioMeasure;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live-server")
public class TestIfc4TwoDimensional {
	@Test
	public void test() throws Exception {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				SProject project = client.getServiceInterface().addProject(RandomStringUtils.randomAlphanumeric(10), "ifc4");
				SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
				Path path = Paths.get(System.getProperty("testFileLocation"), "TestData/data/ifc4add2tc1/tessellation-with-individual-colors.ifc");
				SLongCheckinActionState actionState = client.checkinSync(project.getOid(), "test", deserializer.getOid(), path, (title, progress) ->
						System.out.println(title + ": " + progress)
				);
				ClientIfcModel model = client.getModel(project, actionState.getRoid(), true, false);
				List<IfcColourRgbList> colourRgbLists = model.getAll(IfcColourRgbList.class);
				Assertions.assertFalse(colourRgbLists.isEmpty());
				for (IfcColourRgbList colourRgbList : colourRgbLists) {
				    Assertions.assertEquals(3, colourRgbList.getColourList().size());
					for (ListOfIfcNormalisedRatioMeasure rgbValues : colourRgbList.getColourList()) {
						Assertions.assertEquals(3, rgbValues.getList().size());
						for (IfcNormalisedRatioMeasure ifcNormalisedRatioMeasure : rgbValues.getList()) {
						    Assertions.assertNotNull(ifcNormalisedRatioMeasure);
						}
					}
				}
			}
			Thread.sleep(500);
		}
	}
}
