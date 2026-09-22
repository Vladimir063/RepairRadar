package ru.repairradar.address;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import ru.repairradar.dto.AddressRow;
import ru.repairradar.dto.ImportResult;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AddressStoreIT extends PostgresIntegrationSupport {
    @Test void schemaHasThirteenColumnsAndUuidPrimaryKey() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='public' and table_name='addresses'", Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_schema='public' and table_name='addresses' and column_name='house_guid'", String.class)).isEqualTo("uuid");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conrelid='public.addresses'::regclass and contype='p'", String.class)).isEqualTo("PRIMARY KEY (house_guid)");
    }
    @Test void appendsAndPreservesPreviousRows() {
        var candidates = rows(2500,1000);
        assertThat(store.append(candidates,new Random(1))).isEqualTo(new ImportResult(1000,1000,1000,false));
        var before = jdbc.queryForList("select * from addresses order by house_guid");
        assertThat(store.append(candidates,new Random(2))).isEqualTo(new ImportResult(1000,1000,2000,false));
        assertThat(jdbc.queryForList("select * from addresses order by house_guid")).containsAll(before);
    }
    @Test void returnsRemainderAndThenZero() {
        var candidates = rows(1500,1000);
        store.append(candidates,new Random(1));
        assertThat(store.append(candidates,new Random(2))).isEqualTo(new ImportResult(1000,500,1500,true));
        assertThat(store.append(candidates,new Random(3))).isEqualTo(new ImportResult(1000,0,1500,true));
    }
    @Test void duplicateNativeInsertReturnsZeroWithoutOverwriting() {
        var row = rows(1,1000).getFirst();
        var changed = withAddress(row, "другой адрес");
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(repository.insertIfAbsent(mapper.toEntity(row))).isEqualTo(1);
            assertThat(repository.insertIfAbsent(mapper.toEntity(changed))).isZero();
        });
        assertThat(jdbc.queryForObject("select full_address from addresses",String.class)).isEqualTo(row.fullAddress());
        assertThat(repository.findById(row.houseGuid())).isPresent();
        var result = jdbc.queryForMap("select * from addresses");
        assertThat(result.get("additional_number_1")).isNull();
        assertThat(result.get("additional_type_1")).isNull();
        assertThat(result.get("house_guid")).isEqualTo(row.houseGuid());
    }
    @Test void duplicateCandidatesCountOnce() {
        var row = rows(1,1000).getFirst();
        assertThat(store.append(List.of(row,withAddress(row,"changed")),new Random(1)))
                .isEqualTo(new ImportResult(1000,1,1,true));
        assertThat(jdbc.queryForObject("select full_address from addresses",String.class)).isEqualTo(row.fullAddress());
    }
    @Test void mapsBothAdditionalNumbersAndTypes() {
        var row = new AddressRow("Москва","г. Москва, д. 12/3А, корп. 2Б, стр. 7", "ул. Тестовая",
                "12/3А","2Б",1,"7",2,2,2,new UUID(0,2),1000,new UUID(0,1000));
        store.append(List.of(row),new Random(1));
        var actual = jdbc.queryForMap("select * from addresses");
        assertThat(actual).containsEntry("house_number","12/3А")
                .containsEntry("additional_number_1","2Б").containsEntry("additional_type_1",1)
                .containsEntry("additional_number_2","7").containsEntry("additional_type_2",2);
    }
    @Test void rollsBackNewInsertsOnLaterFailure() {
        store.append(rows(1000,1000),new Random(1));
        var before = jdbc.queryForList("select * from addresses order by house_guid");
        var broken = rows(1000,2000);
        broken.set(99,withAddress(broken.getLast()," "));
        assertThatThrownBy(() -> store.append(broken,new Random(1))).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForList("select * from addresses order by house_guid")).isEqualTo(before);
    }
    @Test void sameStreetAndHouseNumberWithDifferentGuidsAreAllowed() {
        var row = rows(1,1000).getFirst();
        var another = new AddressRow(row.city(),row.fullAddress(),row.street(),row.houseNumber(),
                null,null,null,null,2,row.streetObjectId(),row.streetGuid(),row.houseObjectId(),new UUID(0,9999));
        assertThat(store.append(List.of(row,another),new Random(1)).imported()).isEqualTo(2);
    }
    @Test void constraintsRejectNullKeyAndEmptyRequiredFields() {
        assertThatThrownBy(() -> jdbc.update("insert into addresses(city,full_address,street,house_number,street_object_id,street_guid,house_object_id,house_guid) values ('city','address','street','1',1,?,1,null)",new UUID(0,1)))
                .isInstanceOf(DataAccessException.class);
        store.append(rows(1,1000),new Random(1));
        for (String column : List.of("city","full_address","street","house_number")) {
            assertThatThrownBy(() -> jdbc.update("update addresses set " + column + "=' '"))
                    .isInstanceOf(DataAccessException.class);
        }
    }
    @Test void clearIsRepeatable() {
        store.append(rows(100,1000),new Random(1));
        store.clear(); store.clear();
        assertThat(repository.count()).isZero();
    }
    @Test void parallelImportsAddTwoDistinctThousands() throws Exception {
        var candidates = rows(2500,1000);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<ImportResult> task = () -> {
                if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return store.append(candidates,new Random());
            };
            var a = executor.submit(task);
            var b = executor.submit(task);
            start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS).imported()).isEqualTo(1000);
            assertThat(b.get(20,TimeUnit.SECONDS).imported()).isEqualTo(1000);
        }
        assertThat(repository.count()).isEqualTo(2000);
        assertThat(repository.findAllHouseGuids()).doesNotHaveDuplicates();
    }
    private static AddressRow withAddress(AddressRow r,String address) {
        return new AddressRow(r.city(),address,r.street(),r.houseNumber(),r.additionalNumber1(),
                r.additionalType1(),r.additionalNumber2(),r.additionalType2(),r.houseType(),
                r.streetObjectId(),r.streetGuid(),r.houseObjectId(),r.houseGuid());
    }
}
