package org.bimserver.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.bimserver.models.ifc2x3tc1.Ifc2x3tc1Package;
import org.bimserver.models.store.ConcreteRevision;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.shared.HashMapVirtualObject;
import org.bimserver.shared.QueryContext;
import org.junit.jupiter.api.Test;

/**
 * An object may be written at most once per revision, so the bookkeeping must keep created, updated and deleted apart.
 */
class TransactionTest {

	private final Transaction transaction = new Transaction(null, null, null, concreteRevision(), null);
	private long nextOid = 1;

	@Test
	void deleteSupersedesEarlierUpdate() {
		HashMapVirtualObject object = newObject();

		transaction.updated(object);
		transaction.deleted(object);

		assertThat(transaction.getUpdated()).isEmpty();
		assertThat(transaction.getDeleted()).containsExactly(object);
		assertThat(transaction.isDeleted(object.getOid())).isTrue();
	}

	@Test
	void updateAfterDeleteIsIgnored() {
		HashMapVirtualObject object = newObject();

		transaction.deleted(object);
		transaction.updated(object);

		assertThat(transaction.getUpdated()).isEmpty();
		assertThat(transaction.getDeleted()).containsExactly(object);
	}

	@Test
	void deletingAnObjectCreatedInTheSameTransactionForgetsIt() {
		HashMapVirtualObject object = newObject();

		transaction.created(object);
		transaction.deleted(object);

		assertThat(transaction.getCreated()).isEmpty();
		assertThat(transaction.getDeleted()).isEmpty();
		assertThat(transaction.get(object.getOid())).isNull();
	}

	@Test
	void updatesToCreatedObjectsStayInCreated() {
		HashMapVirtualObject object = newObject();

		transaction.created(object);
		transaction.updated(object);

		assertThat(transaction.getCreated()).containsExactly(object);
		assertThat(transaction.getUpdated()).isEmpty();
		assertThat(transaction.get(object.getOid())).isSameAs(object);
	}

	private static ConcreteRevision concreteRevision() {
		ConcreteRevision concreteRevision = StoreFactory.eINSTANCE.createConcreteRevision();
		concreteRevision.setId(1);
		return concreteRevision;
	}

	private HashMapVirtualObject newObject() {
		return new HashMapVirtualObject(mock(QueryContext.class), Ifc2x3tc1Package.eINSTANCE.getIfcWall(), nextOid++, UUID.randomUUID());
	}
}
