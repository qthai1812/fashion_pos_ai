import React, { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Search } from "lucide-react";
import { branchAdminRole } from "../../../utils/userRole";

import EmployeeStats from "./EmployeeStats";
import EmployeeTable from "./EmployeeTable";
import {
  AddEmployeeDialog,
  EditEmployeeDialog,
  ResetPasswordDialog,
  PerformanceDialog,
} from "./EmployeeDialogs";
import { useDispatch, useSelector } from "react-redux";
import {
  createBranchEmployee,
  findBranchEmployees,
  updateEmployee,
} from "../../../Redux Toolkit/features/employee/employeeThunks";

const getStatusColor = (status) => {
  if (status === "Active") {
    return "text-green-500";
  } else if (status === "Inactive") {
    return "text-red-500";
  } else {
    return "text-muted-foreground";
  }
};

const BranchEmployees = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [isResetPasswordDialogOpen, setIsResetPasswordDialogOpen] =
    useState(false);
  const [isPerformanceDialogOpen, setIsPerformanceDialogOpen] = useState(false);
  const [selectedEmployee, setSelectedEmployee] = useState(null);
  const dispatch = useDispatch();
  const { branch } = useSelector((state) => state.branch);
  const { employees } = useSelector((state) => state.employee);
  const { userProfile } = useSelector((state) => state.user);

  const handleSearch = (e) => {
    setSearchTerm(e.target.value);
  };

  const filteredEmployees = (employees || []).filter(
    (emp) =>
      emp.fullName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      emp.email?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      emp.phone?.includes(searchTerm) ||
      emp.role?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleAddEmployee = (newEmployeeData) => {
    const targetBranchId = branch?.id || userProfile?.branchId;
    if (targetBranchId) {
      const data = {
        employee: {
          ...newEmployeeData,
          username: newEmployeeData.email.split("@")[0],
        },
        branchId: targetBranchId,
        token: localStorage.getItem("jwt"),
      };
      dispatch(createBranchEmployee(data));
      setIsAddDialogOpen(false);
    }
  };

  const handleEditEmployee = (updatedEmployeeData) => {
    if (selectedEmployee?.id && localStorage.getItem("jwt")) {
      const data = {
        employeeId: selectedEmployee.id,
        employeeDetails: updatedEmployeeData,
        token: localStorage.getItem("jwt"),
      };
      dispatch(updateEmployee(data));
      setIsEditDialogOpen(false);
    }
  };

  useEffect(() => {
    if (branch?.id) {
      dispatch(
        findBranchEmployees({
          branchId: branch.id,
        })
      );
    }
  }, [dispatch, branch?.id]);

  const handleToggleAccess = (employee) => {
    if (employee?.id && localStorage.getItem("jwt")) {
      dispatch(
        updateEmployee({
          employeeId: employee.id,
          employeeDetails: {
            ...employee,
            loginAccess: !employee.loginAccess,
          },
          token: localStorage.getItem("jwt"),
        })
      );
    }
  };

  const handleResetPassword = () => {
    setIsResetPasswordDialogOpen(false);
  };

  const openEditDialog = (employee) => {
    setSelectedEmployee(employee);
    setIsEditDialogOpen(true);
  };

  const openResetPasswordDialog = (employee) => {
    setSelectedEmployee(employee);
    setIsResetPasswordDialogOpen(true);
  };

  const openPerformanceDialog = (employee) => {
    setSelectedEmployee(employee);
    setIsPerformanceDialogOpen(true);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h1 className="text-3xl font-bold tracking-tight">
          Employee Management
        </h1>
        <AddEmployeeDialog
          isAddDialogOpen={isAddDialogOpen}
          setIsAddDialogOpen={setIsAddDialogOpen}
          handleAddEmployee={handleAddEmployee}
          roles={branchAdminRole}
        />
      </div>

      <EmployeeStats employees={employees || []} />

      <Card>
        <CardContent className="p-4">
          <div className="relative w-full max-w-sm">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              type="search"
              placeholder="Search employees by name, email, role..."
              className="pl-8"
              value={searchTerm}
              onChange={handleSearch}
            />
          </div>
        </CardContent>
      </Card>

      <EmployeeTable
        employees={filteredEmployees}
        getStatusColor={getStatusColor}
        handleToggleAccess={handleToggleAccess}
        openEditDialog={openEditDialog}
        openResetPasswordDialog={openResetPasswordDialog}
        openPerformanceDialog={openPerformanceDialog}
      />

      <EditEmployeeDialog
        isEditDialogOpen={isEditDialogOpen}
        setIsEditDialogOpen={setIsEditDialogOpen}
        selectedEmployee={selectedEmployee}
        handleEditEmployee={handleEditEmployee}
        roles={branchAdminRole}
      />

      <ResetPasswordDialog
        isResetPasswordDialogOpen={isResetPasswordDialogOpen}
        setIsResetPasswordDialogOpen={setIsResetPasswordDialogOpen}
        selectedEmployee={selectedEmployee}
        handleResetPassword={handleResetPassword}
      />

      <PerformanceDialog
        isPerformanceDialogOpen={isPerformanceDialogOpen}
        setIsPerformanceDialogOpen={setIsPerformanceDialogOpen}
        selectedEmployee={selectedEmployee}
      />
    </div>
  );
};

export default BranchEmployees;
