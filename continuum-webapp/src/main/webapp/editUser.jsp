<%@ taglib uri="/webwork" prefix="ww" %>
<%@ taglib uri="/tld/extremecomponents" prefix="ec" %>
<%@ taglib uri="continuum" prefix="c1" %>
<script>
  function getData() {
    var addMode_field = document.getElementById('addMode_field');
    var accountId_field = document.getElementById('accountId_field');
    var username_field = document.getElementById('username_field');
    var password_field = document.getElementById('password_field');
    var email_field = document.getElementById('email_field');

    var addMode = document.getElementById('addMode');
    var accountId = document.getElementById('accountId');
    var username = document.getElementById('username');
    var password = document.getElementById('password');
    var email = document.getElementById('email');

    addMode.value = addMode_field.value;
    accountId.value = accountId_field.value;
    username.value = username_field.value;
    password.value = password_field.value;
    email.value = email_field.value;
  }
</script>
<html>
  <ww:i18n name="localization.Continuum">
    <head>
      <ww:if test="addMode == true">
        <title><ww:text name="addUser.page.title"/></title>
      </ww:if>
      <ww:else>
        <title><ww:text name="editUser.page.title"/></title>
      </ww:else>
    </head>
    <body>
      <div id="axial" class="h3">
        <ww:if test="addMode == true">
          <h3><ww:text name="addUser.section.title"/></h3>
        </ww:if>
        <ww:else>
          <h3><ww:text name="editUser.section.title"/></h3>
        </ww:else>
        <div class="axial">
          <ww:form action="editUser.action" method="post">
            <table>
              <tbody>
                <ww:hidden id="addMode_field" name="addMode"/>
                <ww:hidden id="accountId_field" name="accountId"/>
                <ww:textfield id="username_field" label="%{getText('user.username')}" name="username" required="true"/>
                <ww:password id="password_field" label="%{getText('user.password')}" name="password" required="true"/>
                <ww:textfield id="email_field" label="%{getText('user.email')}" name="email" required="true"/>
              </tbody>
            </table>
            <div class="functnbar3">
              <c1:submitcancel value="%{getText('save')}" cancel="%{getText('cancel')}"/>
            </div>
          </ww:form>
          <div id="h3">
            <h3><ww:text name="role.section.title"/></h3>
            <ww:set name="permissions" value="permissions" scope="session"/>
              <table>
                <tr>
                  <td><ww:text name="role.rolename"/></td>
                  <td>&nbsp;</td>
                </tr>
                <ww:iterator value="permissions">
                <tr>
                  <td><ww:property value="name"/></td>
                  <td>
                    <ww:form action="editUser!doDeletePermission.action" method="post">
                      <ww:hidden id="addMode" name="addMode"/>
                      <ww:hidden id="accountId" name="accountId"/>
                      <ww:hidden id="username" name="username"/>
                      <ww:hidden id="password" name="password"/>
                      <ww:hidden id="email" name="email"/>
                      <input type="hidden" name="permissionName" value="<ww:property value="name"/>">
                      <ww:submit onclick="getData()" value="%{getText('delete')}"/>
                    </ww:form>
                  </td>
                </tr>
                </ww:iterator>
              </table>
            </div>
          <div id="h3">
            <ww:form action="editUser!doGetAvailablePermissions.action" method="post">
              <ww:hidden id="addMode" name="addMode"/>
              <ww:hidden id="accountId" name="accountId"/>
              <ww:hidden id="username" name="username"/>
              <ww:hidden id="password" name="password"/>
              <ww:hidden id="email" name="email"/>
              <ww:submit onclick="getData()" value="%{getText('add')}"/>
            </ww:form>
          </div>
        </div>
      </div>
    </body>
  </ww:i18n>
</html>