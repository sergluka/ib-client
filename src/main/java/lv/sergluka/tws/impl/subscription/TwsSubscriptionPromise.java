package lv.sergluka.tws.impl.subscription;

import lv.sergluka.tws.impl.promise.TwsPromise;

public interface TwsSubscriptionPromise<T> extends TwsSubscription, TwsPromise<T> {
}
