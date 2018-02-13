package lv.sergluka.ib.impl.subscription;

import java.util.concurrent.Future;

public interface IbSubscriptionFuture<T> extends IbSubscription, Future<T>
{
}
