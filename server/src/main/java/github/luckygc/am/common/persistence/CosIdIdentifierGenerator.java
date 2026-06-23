package github.luckygc.am.common.persistence;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import me.ahoo.cosid.IdGenerator;

public class CosIdIdentifierGenerator implements IdentifierGenerator {

    private static volatile IdGenerator idGenerator;

    public static void setIdGenerator(IdGenerator idGenerator) {
        CosIdIdentifierGenerator.idGenerator = idGenerator;
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        IdGenerator generator = idGenerator;
        if (generator == null) {
            throw new IllegalStateException("CosId IdGenerator 未初始化");
        }
        return generator.generate();
    }
}
