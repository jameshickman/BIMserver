package org.bimserver.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bimserver.emf.PackageMetaData;
import org.bimserver.emf.Schema;
import org.bimserver.models.ifc4.Ifc4Package;
import org.bimserver.plugins.deserializers.DatabaseInterface;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Serialization of wrapped values (IFC defined types) embedded in list references, see issue #1268.
 */
@ExtendWith(MockitoExtension.class)
class HashMapVirtualObjectTest {

	private static final Ifc4Package IFC4 = Ifc4Package.eINSTANCE;

	@TempDir
	static Path tempDir;

	private static PackageMetaData packageMetaData;

	@Mock
	private DatabaseInterface databaseInterface;

	private final Map<EClass, Short> cids = new HashMap<>();
	private final Map<Long, EClass> eClassesByOid = new HashMap<>();
	private QueryContext queryContext;

	@BeforeAll
	static void loadSchema() {
		packageMetaData = new PackageMetaData(IFC4, Schema.IFC4, tempDir);
	}

	@BeforeEach
	void setUp() throws Exception {
		lenient().when(databaseInterface.getCidOfEClass(any())).thenAnswer(invocation -> cidOf(invocation.getArgument(0)));
		lenient().when(databaseInterface.getEClassForOid(anyLong())).thenAnswer(invocation -> eClassesByOid.get(invocation.<Long>getArgument(0)));
		queryContext = new QueryContext(databaseInterface, packageMetaData, 1, 1, 1L, 1L, 1);
	}

	@Nested
	@DisplayName("IfcIndexedPolyCurve.Segments")
	class IndexedPolyCurveSegments {

		@Test
		@DisplayName("writes IfcLineIndex and IfcArcIndex as embedded lists of indices")
		void writesSegmentsAsEmbeddedLists() throws Exception {
			HashMapVirtualObject curve = newObject(IFC4.getIfcIndexedPolyCurve());
			curve.addListItem(IFC4.getIfcIndexedPolyCurve_Segments(), wrapped(IFC4.getIfcLineIndex(), List.of(1L, 2L)));
			curve.addListItem(IFC4.getIfcIndexedPolyCurve_Segments(), wrapped(IFC4.getIfcArcIndex(), List.of(2L, 3L, 4L)));

			byte[] written = toBytes(curve.write());

			assertThat(written)
					.containsSubsequence(embeddedLongList(IFC4.getIfcLineIndex(), 1L, 2L))
					.containsSubsequence(embeddedLongList(IFC4.getIfcArcIndex(), 2L, 3L, 4L));
		}
	}

	@Nested
	@DisplayName("IfcPropertyListValue.ListValues")
	class PropertyListValues {

		@Test
		@DisplayName("still writes single-valued wrapped values as plain wrapped values")
		void writesSingleValuedWrappedValues() throws Exception {
			HashMapVirtualObject property = newObject(IFC4.getIfcPropertyListValue());
			property.addListItem(IFC4.getIfcPropertyListValue_ListValues(), wrapped(IFC4.getIfcLabel(), "first"));
			property.addListItem(IFC4.getIfcPropertyListValue_ListValues(), wrapped(IFC4.getIfcLabel(), "second"));

			byte[] written = toBytes(property.write());

			assertThat(written)
					.containsSubsequence(wrappedString(IFC4.getIfcLabel(), "first"))
					.containsSubsequence(wrappedString(IFC4.getIfcLabel(), "second"));
		}
	}

	private HashMapVirtualObject newObject(EClass eClass) {
		long oid = 1000L + eClassesByOid.size();
		eClassesByOid.put(oid, eClass);
		return new HashMapVirtualObject(queryContext, eClass, oid, UUID.randomUUID());
	}

	private HashMapVirtualObject wrapped(EClass eClass, Object value) {
		HashMapVirtualObject wrapped = new HashMapVirtualObject(queryContext, eClass, -1, null);
		wrapped.setAttribute((EAttribute) eClass.getEStructuralFeature("wrappedValue"), value);
		return wrapped;
	}

	private short cidOf(EClass eClass) {
		return cids.computeIfAbsent(eClass, c -> (short) (100 + cids.size()));
	}

	private byte[] embeddedLongList(EClass eClass, long... values) {
		ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 8 * values.length);
		putNegativeCid(buffer, eClass);
		buffer.putInt(values.length);
		for (long value : values) {
			buffer.putLong(value);
		}
		return buffer.array();
	}

	private byte[] wrappedString(EClass eClass, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + bytes.length);
		putNegativeCid(buffer, eClass);
		buffer.putInt(bytes.length);
		buffer.put(bytes);
		return buffer.array();
	}

	private void putNegativeCid(ByteBuffer buffer, EClass eClass) {
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort((short) -cidOf(eClass));
		buffer.order(ByteOrder.BIG_ENDIAN);
	}

	private static byte[] toBytes(ByteBuffer buffer) {
		assertThat(buffer.remaining()).as("write() must fill the buffer it sized").isZero();
		return buffer.array();
	}
}
