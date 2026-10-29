import { useState, useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useToast } from "@/components/ui/use-toast";
import { getUserProfile } from "@/Redux Toolkit/features/user/userThunks";
import api from "@/utils/api";

export const useSettingsState = () => {
  const dispatch = useDispatch();
  const { userProfile, loading } = useSelector((state) => state.user);
  const { toast } = useToast();

  const [profileData, setProfileData] = useState({
    fullName: "",
    email: "",
    phone: "",
  });

  useEffect(() => {
    const token = localStorage.getItem("jwt");
    if (token) {
      dispatch(getUserProfile(token));
    }
  }, [dispatch]);

  useEffect(() => {
    if (userProfile) {
      setProfileData({
        fullName: userProfile.fullName || "",
        email: userProfile.email || "",
        phone: userProfile.phone || userProfile.mobile || "",
      });
    }
  }, [userProfile]);

  const [passwordData, setPasswordData] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });

  const [showPasswords, setShowPasswords] = useState({
    current: false,
    new: false,
    confirm: false,
  });

  const [notifications, setNotifications] = useState({
    newStoreRequests: true,
    storeApprovals: true,
    commissionUpdates: false,
    systemAlerts: true,
    emailNotifications: true,
  });

  const [systemSettings, setSystemSettings] = useState({
    autoApproveStores: false,
    requireDocumentVerification: true,
    commissionAutoCalculation: true,
    maintenanceMode: false,
  });

  const handleProfileUpdate = async () => {
    try {
      await api.put("/api/users/profile", {
        fullName: profileData.fullName,
        phone: profileData.phone,
      });
      const token = localStorage.getItem("jwt");
      if (token) {
        dispatch(getUserProfile(token));
      }
      toast({
        title: "Profile Updated",
        description: "Your profile information has been updated successfully.",
      });
    } catch (err) {
      toast({
        title: "Update Failed",
        description: err.response?.data?.message || "Failed to update profile",
        variant: "destructive",
      });
    }
  };

  const handlePasswordChange = async () => {
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      toast({
        title: "Password Mismatch",
        description: "New password and confirm password do not match.",
        variant: "destructive",
      });
      return;
    }
    if (passwordData.newPassword.length < 6) {
      toast({
        title: "Weak Password",
        description: "Password must be at least 6 characters long.",
        variant: "destructive",
      });
      return;
    }

    try {
      await api.put("/api/users/change-password", {
        currentPassword: passwordData.currentPassword,
        newPassword: passwordData.newPassword,
      });
      toast({
        title: "Password Changed",
        description: "Your password has been changed successfully.",
      });
      setPasswordData({
        currentPassword: "",
        newPassword: "",
        confirmPassword: "",
      });
    } catch (err) {
      toast({
        title: "Password Change Failed",
        description: err.response?.data?.message || "Could not change password",
        variant: "destructive",
      });
    }
  };

  useEffect(() => {
    const savedNotif = localStorage.getItem("superadmin_notifications");
    if (savedNotif) {
      try {
        setNotifications(JSON.parse(savedNotif));
      } catch (_) {}
    }
    const savedSys = localStorage.getItem("superadmin_system_settings");
    if (savedSys) {
      try {
        setSystemSettings(JSON.parse(savedSys));
      } catch (_) {}
    }
  }, []);

  const handleNotificationToggle = (key) => {
    setNotifications((prev) => {
      const updated = { ...prev, [key]: !prev[key] };
      localStorage.setItem("superadmin_notifications", JSON.stringify(updated));
      toast({
        title: "Notification Preference Saved",
        description: `Setting "${key}" has been ${updated[key] ? "enabled" : "disabled"}.`,
      });
      return updated;
    });
  };

  const handleSystemSettingToggle = (key) => {
    setSystemSettings((prev) => {
      const updated = { ...prev, [key]: !prev[key] };
      localStorage.setItem(
        "superadmin_system_settings",
        JSON.stringify(updated),
      );
      toast({
        title: "System Setting Updated",
        description: `Setting "${key}" has been ${updated[key] ? "enabled" : "disabled"}.`,
      });
      return updated;
    });
  };

  const handleProfileFieldChange = (field, value) => {
    setProfileData((prev) => ({ ...prev, [field]: value }));
  };

  const handlePasswordFieldChange = (field, value) => {
    setPasswordData((prev) => ({ ...prev, [field]: value }));
  };

  const handleShowPasswordToggle = (field) => {
    setShowPasswords((prev) => ({ ...prev, [field]: !prev[field] }));
  };

  return {
    profileData,
    loading,
    passwordData,
    showPasswords,
    notifications,
    systemSettings,
    handleProfileUpdate,
    handlePasswordChange,
    handleNotificationToggle,
    handleSystemSettingToggle,
    handleProfileFieldChange,
    handlePasswordFieldChange,
    handleShowPasswordToggle,
  };
};
