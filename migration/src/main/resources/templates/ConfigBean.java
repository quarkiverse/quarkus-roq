package {packageName};

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
@Unremovable
public class {className} {

    @Inject
    Instance<{interfaceName}> configInstance;

    @Produces
    @Named("{beanName}")
    @Unremovable
    public {interfaceName} produce{interfaceName}() {
        for (Instance.Handle<{interfaceName}> handle : configInstance.handles()) {
            if (handle.getBean().getQualifiers().stream().noneMatch(q -> q instanceof Named)) {
                return handle.get();
            }
        }
        throw new IllegalStateException("{interfaceName} not found");
    }
}
