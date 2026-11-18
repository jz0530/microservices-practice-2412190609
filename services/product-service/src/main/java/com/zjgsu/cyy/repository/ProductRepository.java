package com.zjgsu.cyy.repository;

import com.zjgsu.cyy.model.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) /* 悲观写锁 */
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(@Param("id") String id);
    /* 通过悲观锁查询商品，确保在事务中锁住这条记录，防止其他事务修改它。 */
}
