package io.github.headlesshq.headlessmc.lwjgl;

import io.github.headlesshq.headlessmc.lwjgl.api.Redirection;
import io.github.headlesshq.headlessmc.lwjgl.api.RedirectionManager;
import io.github.headlesshq.headlessmc.lwjgl.redirections.CastRedirection;
import io.github.headlesshq.headlessmc.lwjgl.redirections.DefaultRedirections;
import io.github.headlesshq.headlessmc.lwjgl.redirections.LwjglRedirections;
import io.github.headlesshq.headlessmc.lwjgl.redirections.ObjectRedirection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class RedirectionManagerImpl implements RedirectionManager {
    private final Map<String, Redirection> redirects = new HashMap<>();
    private final Redirection object = new ObjectRedirection(this);
    private final Redirection cast = new CastRedirection(this);

    public RedirectionManagerImpl() {
        LwjglRedirections.register(this);
    }

    @Override
    public void redirect(String desc, Redirection redirection) {
        redirects.put(desc, redirection);
    }

    @Override
    public Object invoke(Object obj, String desc, Class<?> type, Object... args)
        throws Throwable {
        return invoke(desc, type, obj, () -> getFallback(desc, type), args);
    }

    @Override
    public Object invoke(String desc, Class<?> type, Object obj,
                         Supplier<Redirection> fb, Object... args)
        throws Throwable {
        Redirection redirection = redirects.get(desc);
        if (redirection == null) {
            // MC 26.2 funnels GL calls through the core-profile classes (e.g.
            // GlStateManager -> GL33C.glCreateProgram), so the redirected method
            // body lives in the declaring core class (GL20C.glCreateProgram).
            // Our redirects are registered under the compatibility classes
            // (GL20). When there's no exact match, retry against the GLnn
            // variant of a GLnnC descriptor.
            String compat = coreToCompatDescriptor(desc);
            if (compat != null) {
                redirection = redirects.get(compat);
            }
        }

        if (redirection == null) {
            redirection = fb.get();
        }

        return redirection.invoke(obj, desc, type, args);
    }

    /**
     * Maps a {@code Lorg/lwjgl/opengl/GLnnC;...} descriptor to its
     * {@code Lorg/lwjgl/opengl/GLnn;...} compatibility-class equivalent, or
     * {@code null} if {@code desc} is not such a core-class descriptor.
     */
    private static String coreToCompatDescriptor(String desc) {
        int sep = desc.indexOf(';');
        if (sep < 2
            || !desc.startsWith("Lorg/lwjgl/opengl/GL")
            || desc.charAt(sep - 1) != 'C'
            || !Character.isDigit(desc.charAt(sep - 2))) {
            return null;
        }

        return desc.substring(0, sep - 1) + desc.substring(sep);
    }

    private Redirection getFallback(String desc, Class<?> type) {
        if (desc.startsWith(Redirection.CAST_PREFIX)) {
            // TODO: currently cast redirection looks like this:
            //  <cast> java/lang/String
            //  <init> <cast> java/lang/String
            //  It contains no information about the calling class!
            return cast;
        }

        return DefaultRedirections.fallback(type, object);
    }

}
