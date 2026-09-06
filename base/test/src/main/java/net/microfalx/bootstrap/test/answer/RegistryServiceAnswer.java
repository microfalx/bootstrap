package net.microfalx.bootstrap.test.answer;

import net.microfalx.bootstrap.registry.RegistryService;
import net.microfalx.bootstrap.test.annotation.AnswerFor;
import net.microfalx.registry.Registry;
import net.microfalx.registry.Storage;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;

@SuppressWarnings("unused")
@AnswerFor(RegistryService.class)
public class RegistryServiceAnswer extends AbstractAnswer {

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        return switch (name) {
            case "getRegistry" -> getRegistry();
            case "getStorage" -> getStorage();
            case "getStorages" -> List.of(getStorage());
            default -> super.answer(invocation);
        };
    }

    private Registry getRegistry() {
        return resolve(Registry.class);
    }

    private Storage getStorage() {
        return getRegistry().getStorage();
    }
}
