import React, { useEffect, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { Button } from "../../../components/ui/button";
import {
  CheckCircle,
  Store,
  Users,
  ShoppingCart,
  Info,
  Star,
} from "lucide-react";
import { getAllSubscriptionPlans } from "../../../Redux Toolkit/features/subscriptionPlan/subscriptionPlanThunks";
import {
  subscribeToPlan,
  upgradeSubscription,
  getStoreSubscriptions,
} from "../../../Redux Toolkit/features/subscription/subscriptionThunks";

const Upgrade = () => {
  const dispatch = useDispatch();
  const {
    plans,
    loading: plansLoading,
    error: plansError,
  } = useSelector((state) => state.subscriptionPlan);
  const {
    subscriptions,
    loading: subLoading,
    error: subError,
  } = useSelector((state) => state.subscription);
  const { store } = useSelector((state) => state.store);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Supports both flat SubscriptionDTO ({ storeId, planId, planName, status }) and nested objects
  const currentSubscription = subscriptions?.find(
    (sub) =>
      (sub.storeId === store?.id || sub.store?.id === store?.id) &&
      sub.status === "ACTIVE"
  );

  const isCurrentPlan = (plan) => {
    if (!currentSubscription || !plan) return false;
    return (
      String(currentSubscription.planId) === String(plan.id) ||
      currentSubscription.plan?.id === plan.id ||
      currentSubscription.planName?.toLowerCase() === plan.name?.toLowerCase()
    );
  };

  useEffect(() => {
    dispatch(getAllSubscriptionPlans());
    if (store?.id) {
      dispatch(
        getStoreSubscriptions({ storeId: store.id, status: "ACTIVE" })
      );
    }
  }, [dispatch, store?.id]);

  const handleSubscribe = async (planId) => {
    if (!store?.id) return;
    setActionLoading(true);
    setActionError(null);
    setSuccessMsg(null);
    try {
      if (currentSubscription) {
        await dispatch(
          upgradeSubscription({ storeId: store.id, planId })
        ).unwrap();
        setSuccessMsg("Subscription upgraded successfully!");
      } else {
        await dispatch(
          subscribeToPlan({ storeId: store.id, planId })
        ).unwrap();
        setSuccessMsg("Subscribed successfully!");
      }
      dispatch(
        getStoreSubscriptions({ storeId: store.id, status: "ACTIVE" })
      );
    } catch (err) {
      setActionError(
        (err && err.message) || err || "Failed to subscribe/upgrade."
      );
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto py-12 px-4">
      <h1 className="text-3xl font-bold mb-6 text-center">
        Upgrade Your Subscription
      </h1>
      {plansLoading || subLoading ? (
        <div className="text-center py-8">Loading...</div>
      ) : plansError || subError ? (
        <div className="text-center text-red-500 py-8">
          {plansError || subError}
        </div>
      ) : (
        <>
          {currentSubscription && (
            <div className="mb-8 p-4 bg-emerald-500/10 border border-emerald-500/30 rounded-lg text-foreground flex items-center gap-3">
              <CheckCircle className="w-6 h-6 text-emerald-500 flex-shrink-0" />
              <div>
                <div className="font-semibold">
                  Current Plan:{" "}
                  {currentSubscription.planName ||
                    currentSubscription.plan?.name}
                </div>
                <div className="text-sm text-muted-foreground">
                  Status: {currentSubscription.status} | Valid till:{" "}
                  {currentSubscription.endDate}
                </div>
              </div>
            </div>
          )}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {plans?.map((plan) => {
              const activePlan = isCurrentPlan(plan);
              return (
                <div
                  key={plan.id}
                  className={`bg-card rounded-2xl p-8 shadow-lg border relative flex flex-col ${
                    activePlan ? "ring-2 ring-primary" : ""
                  }`}
                >
                  {activePlan && (
                    <div className="absolute -top-4 left-1/2 transform -translate-x-1/2">
                      <span className="bg-primary text-primary-foreground px-4 py-1 rounded-full text-sm font-medium">
                        Your Plan
                      </span>
                    </div>
                  )}
                  <div className="text-center mb-8">
                    <h3 className="text-2xl font-bold text-foreground mb-2">
                      {plan.name}
                    </h3>
                    <div className="flex items-baseline justify-center">
                      <span className="text-4xl font-bold text-foreground">
                        đ{Number(plan.price || 0).toLocaleString()}
                      </span>
                      <span className="text-muted-foreground ml-1">
                        /{plan.billingCycle?.toLowerCase()}
                      </span>
                    </div>
                  </div>
                  <ul className="space-y-4 mb-8">
                    {plan.description && (
                      <li className="text-muted-foreground mb-2 flex items-center gap-2">
                        <Info className="w-5 h-5 text-blue-500 flex-shrink-0" />
                        {plan.description}
                      </li>
                    )}
                    {plan.maxBranches && (
                      <li className="text-muted-foreground flex items-center gap-2">
                        <Store className="w-5 h-5 text-purple-500 flex-shrink-0" />
                        Max Branches: {plan.maxBranches}
                      </li>
                    )}
                    {plan.maxUsers && (
                      <li className="text-muted-foreground flex items-center gap-2">
                        <Users className="w-5 h-5 text-orange-500 flex-shrink-0" />
                        Max Users: {plan.maxUsers}
                      </li>
                    )}
                    {plan.maxProducts && (
                      <li className="text-muted-foreground flex items-center gap-2">
                        <ShoppingCart className="w-5 h-5 text-green-500 flex-shrink-0" />
                        Max Products: {plan.maxProducts}
                      </li>
                    )}
                    {plan.extraFeatures && plan.extraFeatures.length > 0 && (
                      <li className="text-muted-foreground flex flex-col gap-1 mt-2">
                        <span className="font-medium flex items-center gap-2">
                          <Star className="w-5 h-5 text-yellow-500" />
                          Extra Features:
                        </span>
                        <ul className="ml-7 list-disc space-y-1">
                          {plan.extraFeatures.map((feature, idx) => (
                            <li key={idx} className="flex items-center gap-2">
                              <Star className="w-4 h-4 text-yellow-400" />
                              <span>{feature}</span>
                            </li>
                          ))}
                        </ul>
                      </li>
                    )}
                  </ul>
                  <Button
                    className="w-full mt-auto"
                    variant={activePlan ? "outline" : "default"}
                    size="default"
                    disabled={actionLoading || activePlan}
                    onClick={() => handleSubscribe(plan.id)}
                  >
                    {activePlan
                      ? "Current Plan"
                      : currentSubscription
                      ? "Upgrade to this Plan"
                      : "Subscribe"}
                  </Button>
                </div>
              );
            })}
          </div>
          {actionError && (
            <div className="text-center text-red-500 mt-6">{actionError}</div>
          )}
          {successMsg && (
            <div className="text-center text-green-600 mt-6">{successMsg}</div>
          )}
        </>
      )}
    </div>
  );
};

export default Upgrade;
