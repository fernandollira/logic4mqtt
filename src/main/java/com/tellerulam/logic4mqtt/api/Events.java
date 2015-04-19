/*
 * This object contains the interface from scripts to the Event handling system
 */

package com.tellerulam.logic4mqtt.api;

import java.util.*;

import com.tellerulam.logic4mqtt.*;
import com.tellerulam.logic4mqtt.TopicCache.TopicValue;

public class Events
{
	private static final Events instance=new Events();

	public static Events getInstance()
	{
		return instance;
	}

	/**
	 *
	 * Add an Event Handler on the specified topic pattern. It is triggered when the
	 * topic changes to the specified value.
	 *
	 * @param topicPattern a RegEx matching topics
	 * @param value
	 * @param callback
	 * @return id of the new handler (to be used with e.g. remove)
	 */
	public int onChangeTo(String topicPattern,Object value,EventCallbackInterface callback)
	{
		topicPattern=TopicCache.convertStatusTopic(topicPattern);
		return EventHandler.createNewHandler(topicPattern, new Object[]{value}, true, callback, false, null);
	}
	/**
	 * Add an Event Handler on the specified topic pattern. It is triggered when the
	 * topic changes to any of the the specified values.
	 *
	 * @param topicPattern a RegEx matching topics
	 * @param values
	 * @param callback
	 * @return id of the new handler (to be used with e.g. remove)
	 */
	public int onChangeTo(String topicPattern,Object values[],EventCallbackInterface callback)
	{
		topicPattern=TopicCache.convertStatusTopic(topicPattern);
		return EventHandler.createNewHandler(topicPattern, values, true, callback, false, null);
	}
	/**
	 * Add an Event Handler on the specified topic pattern. It is triggered when the
	 * topic changes from the previous value.
	 *
	 * @param topicPattern a RegEx matching topics
	 * @param callback
	 * @return id of the new handler (to be used with e.g. remove)
	 */
	public int onChange(String topicPattern,EventCallbackInterface callback)
	{
		topicPattern=TopicCache.convertStatusTopic(topicPattern);
		return EventHandler.createNewHandler(topicPattern, null, true, callback, false, null);
	}
	/**
	 * Add an Event Handler on the specified topic pattern. It is triggered when the
	 * topic is being published to, regardless of the value.
	 *
	 * @param topicPattern a RegEx matching topics
	 * @param callback
	 * @return id of the new handler (to be used with e.g. remove)
	 */
	public int onUpdate(String topicPattern,EventCallbackInterface callback)
	{
		topicPattern=TopicCache.convertStatusTopic(topicPattern);
		return EventHandler.createNewHandler(topicPattern, null, false, callback, false, null);
	}

	/**
	 * Add an Event Handler using a description object (e.g. JSON object from Javascript)
	 *
	 * @param topicPattern
	 * @param callback
	 * @param params Map with parameters:
	 *   values - value or array of values which trigger the event
	 *   change - bool: whether to run only on changes of the value
	 *   oneshot - bool: whether to remove the event handler after it run once
	 *
	 * @return id of the new handler (to be used with e.g. remove)
	 */
	@SuppressWarnings("unchecked")
	public int add(String topicPattern,EventCallbackInterface callback,Map<String,Object> params)
	{
		Object values=params.get("values");
		Object vals[];
		if(values!=null)
		{
			if(values instanceof Map)
			{
				vals=((Map<String,Object>)values).values().toArray();
			}
			else
				vals=new Object[]{values};
		}
		else
			vals=null;

		boolean oneShot=ScriptEngineTools.interpretAsBoolean(params.get("oneshot"));
		boolean change=ScriptEngineTools.interpretAsBoolean(params.get("change"));

		String expires=(String)params.get("expires");

		topicPattern=TopicCache.convertStatusTopic(topicPattern);
		int eid=EventHandler.createNewHandler(topicPattern, vals, change, callback, oneShot, expires);

		return eid;
	}

	/**
	 * Removes an event handler which was added with one of the on...() methods.
	 *
	 * @param id
	 * @return boolean whether an event handler with the given id was found and removed
	 */
	public boolean remove(int id)
	{
		return EventHandler.removeByID(id);
	}

	/**
	 *
	 * Publish an update to the specified topic with the given value. It will not be retained.
	 *
	 * @param topic
	 * @param value
	 */
	public void setValue(String topic,Object value)
	{
		topic=TopicCache.convertSetTopic(topic);
		MQTTHandler.doPublish(topic, value, false);
	}

	public void setValue(String topic,Map<String,Object> value)
	{
		topic=TopicCache.convertSetTopic(topic);
		MQTTHandler.doPublish(topic, ScriptEngineTools.encodeAsJSON(value), false);
	}

	/**
	 * Publish an update to the specified topic with the given value. It will be retained.
	 *
	 * @param topic
	 * @param value
	 */
	public void storeValue(String topic,Object value)
	{
		topic=TopicCache.convertSetTopic(topic);
		MQTTHandler.doPublish(topic, value, true);
	}

	public void storeValue(String topic,Map<String,Object> value)
	{
		topic=TopicCache.convertSetTopic(topic);
		MQTTHandler.doPublish(topic, ScriptEngineTools.encodeAsJSON(value), true);
	}

	private static class QueuedSet implements TimerCallbackInterface
	{
		final String setTopic;
		final Object value;
		final boolean retain;
		@Override
		public void run(Object userdata)
		{
			MQTTHandler.doPublish(setTopic, value, retain);
		}
		@Override
		public String toString()
		{
			return "{QuSet:="+value+(retain?"/R":""+"}");
		}
		protected QueuedSet(String setTopic, Object value, boolean retain)
		{
			this.setTopic = setTopic;
			this.value = value;
			this.retain = retain;
		}
	}

	public void internalQueueSet(String timespec,String topic,final Object value,final boolean retain)
	{
		final String setTopic=TopicCache.convertSetTopic(topic);
		LogicTimer.addTimer("_SET_"+setTopic, timespec, new QueuedSet(setTopic,value,retain),null);
	}
	/**
	 * Queue an update to the specified topic with the given value at timespec.
	 *
	 * This is effectivly a shortcut for adding a timer which sets the value,
	 * with a symbolic name of _SET_topic
	 *
	 * @param timespec the timer spec
	 * @param topic the topic to publish to
	 * @param value the value to publish
	 *
	 * @see Timers
	 */
	public void queueValue(String timespec,String topic,Object value)
	{
		internalQueueSet(timespec,topic,value,false);
	}

	public void queueValue(String timespec,String topic,Map<String,Object> value)
	{
		internalQueueSet(timespec,topic,ScriptEngineTools.encodeAsJSON(value),false);
	}


	/**
	 * Queue an update to the specified topic with the given value at timespec,
	 * with the retain flag set to true.
	 *
	 * This is effectivly a shortcut for adding a timer which sets the value,
	 * with a symbolic name of _SET_topic
	 *
	 * @param timespec the timer spec
	 * @param topic the topic to publish to
	 * @param value the value to publish
	 *
	 * @see Timers
	 */
	public void queueStore(String timespec,String topic,Object value)
	{
		internalQueueSet(timespec,topic,value,true);
	}

	public void queueStore(String timespec,String topic,Map<String,Object> value)
	{
		internalQueueSet(timespec,topic,ScriptEngineTools.encodeAsJSON(value),true);
	}

	/**
	 * Clears possibly queued value sets for the given topic
	 *
	 * @param topic the topic to clear the queue for
	 * @return number of removed queued set
	 */
	public int clearQueue(String topic)
	{
		return LogicTimer.remTimer("_SET_"+TopicCache.convertSetTopic(topic));
	}

	/**
	 *
	 * Get a cached last value of a topic, or null if the topic or the specified
	 * generation is not known.
	 *
	 * @param topic
	 * @param generation (0 = current, 1 = previous, ...)
	 * @return the value, or null
	 */
	public Object getValue(String topic,int generation)
	{
		topic=TopicCache.convertStatusTopic(topic);
		TopicValue tv=TopicCache.getTopicValue(topic, generation);
		if(tv!=null)
			return tv.value;
		return null;
	}

	/**
	 * Get the current last value of a topic, or null if the topic is not known.
	 * @param topic
	 * @return the value, or null
	 */
	public Object getValue(String topic)
	{
		return getValue(topic,0);
	}

	/**
	 *
	 * Get the timestamp of the value of a topic, or null if the topic or the specified
	 * generation is not known.
	 *
	 * @param topic
	 * @param generation (0 = current, 1 = previous, ...)
	 * @return the timestamp, or null
	 */
	public Date getTimestamp(String topic,int generation)
	{
		topic=TopicCache.convertStatusTopic(topic);
		TopicValue tv=TopicCache.getTopicValue(topic, generation);
		if(tv!=null)
			return tv.ts;
		return null;
	}

	/**
	 * Get the timestamp of the value of a topic, or null if the topic is not known.
	 * @param topic
	 * @return the timestamp, or null
	 */
	public Date getTimestamp(String topic)
	{
		return getTimestamp(topic,0);
	}

	/**
	 * Request a value via a MQTT /get/ publish
	 * @param topic
	 */
	public void requestValue(String topic)
	{
		topic=TopicCache.convertGetTopic(topic);
		MQTTHandler.doPublish(topic, "?", false);
	}
}