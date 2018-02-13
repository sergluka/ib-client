package lv.sergluka.tws.impl.subscription;

import java.util.concurrent.Future;

public interface TwsSubscriptionPromise<T> extends TwsSubscription, Future<T>
{
}
