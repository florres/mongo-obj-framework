package org.smof.parsers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.types.ObjectId;
import org.smof.collection.SmofDispatcher;
import org.smof.element.AbstractElement;
import org.smof.element.Element;
import org.smof.exception.SmofException;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

@SuppressWarnings("javadoc")
public class LazyLoader {

	private static final String DISPATCHER = "dispatcher";
	private static final String DELEGATE = "delegate";
	private static final String LOADED = "loaded";

	private static void handleError(Throwable cause) {
		throw new SmofException(cause);
	}

	static LazyLoader create(SmofDispatcher dispatcher) {
		return new LazyLoader(dispatcher); 
	}

	private final Map<Class<?>, Class<?>> lazyTypes;
	private final SmofDispatcher dispatcher;
	private final ByteBuddy byteBuddy;

	private LazyLoader(SmofDispatcher dispatcher) {
		this.dispatcher = dispatcher;
		this.lazyTypes = new LinkedHashMap<>();
		this.byteBuddy = new ByteBuddy();
	}

	public <T extends Element> T createLazyInstance(Class<T> type, ObjectId id) {
		final Class<? extends T> lazyType = getLazyType(type);
		return createInstance(type, lazyType, id);
	}

	private <T extends Element, E extends T> T createInstance(Class<T> type, Class<E> lazyType, ObjectId id){
		try {
			final T element = lazyType.getConstructor().newInstance();
			((HandlerSetter) element).setHandler(new Handler<>(id, dispatcher, type));
			return element;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			handleError(e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends Element> Class<? extends T> getLazyType(Class<T> type) {
		if(lazyTypes.containsKey(type)) {
			return (Class<? extends T>) lazyTypes.get(type);
		}
		return createLazyType(type);
	}

	private <T extends Element> Class<? extends T> createLazyType(Class<T> type) {
		if(type.isInterface()) {
			return createLazyClass(type);
		}
		return createLazySubClass(type);
	}

	@SuppressWarnings("unchecked")
	private <T> Class<? extends T> createLazyClass(Class<T> type) {
		System.out.println("Creating lazy interface for: " + type.getName());
		Class<? extends Element> lazyType = byteBuddy
				.subclass(AbstractElement.class)
				.implement(type)
				.defineField("handler", InvocationHandler.class, Visibility.PUBLIC)
				.implement(HandlerSetter.class)
				.intercept(FieldAccessor.ofField("handler"))
				.method(ElementMatchers.not(ElementMatchers.isDeclaredBy(HandlerSetter.class)))
				.intercept(InvocationHandlerAdapter.toField("handler"))
				.make()
				.load(type.getClassLoader())
				.getLoaded();
		lazyTypes.put(type, lazyType);
		return (Class<? extends T>) lazyType;
	}

	private <T extends Element> Class<? extends T> createLazySubClass(Class<T> type) {
		System.out.println("not here: " + type.getName());
		return null;
	}
	
	public interface HandlerSetter {
		InvocationHandler getHandler();
		void setHandler(InvocationHandler handler);
	}
	
	public static class Handler<T extends Element> implements InvocationHandler {
		
		private final ObjectId id;
		private final SmofDispatcher dispatcher;
		private final Class<T> delegateClass;
		private boolean loaded;
		private Object delegate;
		
		Handler(ObjectId id, SmofDispatcher dispatcher, Class<T> delegateClass) {
			super();
			this.id = id;
			this.dispatcher = dispatcher;
			this.delegateClass = delegateClass;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if(!loaded) {
				delegate = dispatcher.findById(id, delegateClass);
			}
			return method.invoke(delegate, args);
		}
		
	}
}