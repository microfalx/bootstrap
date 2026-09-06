package net.microfalx.bootstrap.test.answer;

import net.microfalx.bootstrap.registry.RegistryService;
import net.microfalx.bootstrap.test.annotation.AnswerFor;
import net.microfalx.registry.Registry;
import net.microfalx.registry.core.MemoryStorage;
import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;

@SuppressWarnings("unused")
@AnswerFor(Registry.class)
public class RegistryAnswer extends AbstractAnswer {

    private Registry registry;

    @Override
    public void initialize(Object... context) {
        super.initialize(context);
        RegistryService registryService = getContext().lookup(RegistryService.class);
        if (registryService != null && !MockUtil.isMock(registryService)) {
            registry = registryService.getRegistry();
        } else {
            registry = net.microfalx.registry.RegistryService.getInstance()
                    .getRegistry(new MemoryStorage());
        }
    }

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        return invocation.getMethod().invoke(registry, invocation.getArguments());
    }
}
