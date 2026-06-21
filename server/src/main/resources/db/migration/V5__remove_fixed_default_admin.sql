delete from am_auth_user_role_rel rel
using am_auth_user auth_user
where rel.user_id = auth_user.id
  and auth_user.username = 'admin'
  and auth_user.password = '{noop}admin';

delete from am_auth_user
where username = 'admin'
  and password = '{noop}admin';
