import React from "react";
import {
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Edit, UserX, UserCheck, Key, BarChart } from "lucide-react";

const EmployeeTable = ({
  employees,
  handleToggleAccess,
  openResetPasswordDialog,
  openPerformanceDialog,
  openEditDialog,
}) => {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Email</TableHead>
          <TableHead>Login Access</TableHead>
          <TableHead>Assigned Since</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {employees?.length > 0 ? (
          employees.map((employee) => {
            const hasAccess = employee.loginAccess !== false;
            return (
              <TableRow key={employee.id}>
                <TableCell className="font-medium">{employee.fullName}</TableCell>
                <TableCell>{employee.role}</TableCell>
                <TableCell>{employee.email}</TableCell>
                <TableCell>
                  <Badge
                    className={
                      hasAccess
                        ? "bg-green-100 text-green-800 hover:bg-green-100/80"
                        : "bg-red-100 text-red-800 hover:bg-red-100/80"
                    }
                    variant="secondary"
                  >
                    {hasAccess ? "Enabled" : "Disabled"}
                  </Badge>
                </TableCell>
                <TableCell>{employee.createdAt?.slice(0, 10) || "-"}</TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-2">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleToggleAccess(employee)}
                      title={hasAccess ? "Disable Access" : "Enable Access"}
                    >
                      {hasAccess ? (
                        <UserX className="h-4 w-4 text-red-500" />
                      ) : (
                        <UserCheck className="h-4 w-4 text-green-600" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => openResetPasswordDialog(employee)}
                      title="Reset Password"
                    >
                      <Key className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => openPerformanceDialog(employee)}
                      title="View Performance"
                    >
                      <BarChart className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => openEditDialog(employee)}
                      title="Edit Employee"
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            );
          })
        ) : (
          <TableRow>
            <TableCell colSpan={6} className="text-center py-4 text-gray-500">
              No employees found matching your criteria
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  );
};

export default EmployeeTable;
