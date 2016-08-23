/*
 Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 Tobias Schweizer, André Kilchenmann, and André Fatton.

 This file is part of Knora.

 Knora is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published
 by the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Knora is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public
 License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import {Component, OnInit} from "angular2/core";
import {UserService} from "./user.service";
import {User} from "./user";
import {Router} from "angular2/router";

@Component({
    template: `
        <form #myForm="ngForm" (ngSubmit)="onSubmit()">
            <h2>Create New User</h2>
            <div>
                <label for="user-name">Username:</label>
                <input type="text" id="user-name" 
                    ngControl="userName"
                    [(ngModel)]="newUser.userName"
                    required
                >
            </div>
            <div>
                <label for="given-name">Given Name:</label>
                <input type="text" id="given-name"
                    ngControl="givenName"
                    [(ngModel)]="newUser.givenName"
                    required
                >
            </div>
            <div>
                <label for="family-name">Family Name:</label>
                <input type="text" id="family-name"
                    ngControl="familyName"
                    [(ngModel)]="newUser.familyName"
                    required
                >
            </div>
            <div>
                <label for="email">Email:</label>
                <input type="text" id="email"
                    ngControl="email"
                    [(ngModel)]="newUser.email"
                    required
                >
            </div>
            <div>
                <label for="phone">Phone:</label>
                <input type="text" id="phone"
                    ngControl="phone"
                    [(ngModel)]="newUser.phone"
                    required
                >
            </div>
            <div>
                <label for="system-admin-status">System Admin:</label>
                <input type="checkbox" id="system-admin-status" ngControl="systemAdminStatus">
            </div>
            <div>
                <label for="user-status">Active User:</label>
                <input type="checkbox" id="user-status" ngControl="userStatus">
            </div>
            <button type="submit">Create User</button>
        </form>
    `,
    styles: [`
        label {
            display: inline-block;
            width: 110px;
        }
        input {
            width: 250px;
        }
    `],
    providers: [UserService]
})
export class NewUserComponent {

    newUser: User = {
        userId: '',
        userName: '',
        givenName: '',
        familyName: '',
        email: '',
        phone: '',
        isActiveUser: false,
        isSystemAdmin: false
    };

    constructor(private _userService: UserService, private _router: Router) {}

    onSubmit() {
        this._userService.insertUser(this.newUser)
        this._router.navigate(['Users'])
    }
}