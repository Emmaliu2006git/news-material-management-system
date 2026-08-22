package com.example.newsmaterial;

import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.stereotype.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@SpringBootApplication
public class NewsMaterialApplication {
 public static void main(String[] args){SpringApplication.run(NewsMaterialApplication.class,args);}
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean AuthenticationManager authenticationManager(UserDetailsService uds,PasswordEncoder encoder){var p=new DaoAuthenticationProvider(uds);p.setPasswordEncoder(encoder);return new ProviderManager(p);}
 @Bean SecurityContextRepository securityContextRepository(){return new HttpSessionSecurityContextRepository();}
 @Bean SecurityFilterChain filter(HttpSecurity h,SecurityContextRepository repo,JsonMapper json)throws Exception{
  var csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();csrf.setCookiePath("/");
  h.csrf(c->c.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/auth/login"))
   .securityContext(c->c.securityContextRepository(repo).requireExplicitSave(true))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
   .exceptionHandling(e->e.authenticationEntryPoint(new Entry(json)).accessDeniedHandler(new Denied(json)))
   .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/login","/api/v1/auth/csrf","/error").permitAll().requestMatchers("/api/**").authenticated().anyRequest().permitAll());
  return h.build();
 }
 @Bean @Profile("local") ApplicationRunner seed(UserRepository users,PasswordEncoder passwords){return args->{for(String n:List.of("emma","alex"))if(users.findByUsername(n).isEmpty())users.save(new AppUser(UUID.randomUUID(),n,passwords.encode("demo-password"),Instant.now()));};}
}

@Entity @Table(name="app_users")
class AppUser{
 @Id UUID id; @Column(unique=true,nullable=false) String username; @Column(name="password_hash",nullable=false) String passwordHash; @Column(name="created_at",nullable=false) Instant createdAt;
 protected AppUser(){} AppUser(UUID id,String u,String p,Instant t){this.id=id;username=u;passwordHash=p;createdAt=t;}
 UUID getId(){return id;}
 String getUsername(){return username;}
}
interface UserRepository extends JpaRepository<AppUser,UUID>{Optional<AppUser> findByUsername(String username);}

@Service class DbUsers implements UserDetailsService{
 private final UserRepository users; DbUsers(UserRepository u){users=u;}
 public UserDetails loadUserByUsername(String n){AppUser u=users.findByUsername(n).orElseThrow(()->new UsernameNotFoundException(n));return new Principal(u.id,u.username,u.passwordHash);}
}
record Principal(UUID id,String username,String password) implements UserDetails{
 public Collection<? extends GrantedAuthority> getAuthorities(){return List.of(new SimpleGrantedAuthority("ROLE_USER"));}
 public String getUsername(){return username;}
 public boolean isAccountNonExpired(){return true;} public boolean isAccountNonLocked(){return true;} public boolean isCredentialsNonExpired(){return true;} public boolean isEnabled(){return true;}
 public String getPassword(){return password;}
}
@RestController @RequestMapping("/api/v1/auth")
class AuthApi{
 private final AuthenticationManager manager;private final SecurityContextRepository sessions;
 AuthApi(AuthenticationManager m,SecurityContextRepository s){manager=m;sessions=s;}
 @PostMapping("/login") LoginResponse login(@Valid @RequestBody LoginRequest body,HttpServletRequest req,HttpServletResponse res){
  try{Authentication a=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.username(),body.password()));SecurityContext c=SecurityContextHolder.createEmptyContext();c.setAuthentication(a);SecurityContextHolder.setContext(c);sessions.saveContext(c,req,res);Principal p=(Principal)a.getPrincipal();return new LoginResponse(p.id(),p.username());}catch(BadCredentialsException e){throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid username or password");}
 }
 @GetMapping("/csrf") CsrfResponse csrf(HttpServletRequest r){CsrfToken t=(CsrfToken)r.getAttribute(CsrfToken.class.getName());if(t==null)t=(CsrfToken)r.getAttribute("_csrf");return new CsrfResponse(t.getToken(),t.getHeaderName());}
 record LoginRequest(@NotBlank String username,@NotBlank String password){} record LoginResponse(UUID id,String username){} record CsrfResponse(String token,String headerName){}
}

@Entity @Table(name="reporting_projects")
class ReportingProject{
 @Id UUID id; @Column(nullable=false,length=160)String title; @ManyToOne(fetch=FetchType.LAZY)@JoinColumn(name="created_by",nullable=false)AppUser createdBy;@Column(name="created_at",nullable=false)Instant createdAt;@Column(name="updated_at",nullable=false)Instant updatedAt;
 protected ReportingProject(){} ReportingProject(UUID i,String t,AppUser u){id=i;title=t;createdBy=u;createdAt=Instant.now();updatedAt=createdAt;} void rename(String t){title=t;updatedAt=Instant.now();}
}
interface ProjectRepository extends JpaRepository<ReportingProject,UUID>{List<ReportingProject> findAllByOrderByCreatedAtDesc();}
@RestController @RequestMapping("/api/v1/projects")
class ProjectApi{
 private final ProjectRepository projects;private final UserRepository users; ProjectApi(ProjectRepository p,UserRepository u){projects=p;users=u;}
 @GetMapping List<ProjectResponse> list(){return projects.findAllByOrderByCreatedAtDesc().stream().map(ProjectResponse::from).toList();}
 @PostMapping ResponseEntity<ProjectResponse> create(@org.springframework.security.core.annotation.AuthenticationPrincipal Principal p,@Valid @RequestBody ProjectRequest r){AppUser u=users.findById(p.id()).orElseThrow();ReportingProject project=projects.save(new ReportingProject(UUID.randomUUID(),r.title().trim(),u));return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));}
 @PatchMapping("/{id}") @Transactional ProjectResponse rename(@PathVariable UUID id,@org.springframework.security.core.annotation.AuthenticationPrincipal Principal p,@Valid @RequestBody ProjectRequest r){ReportingProject project=projects.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PROJECT_NOT_FOUND","Project was not found"));if(!project.createdBy.getId().equals(p.id()))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","You do not have permission for this action");project.rename(r.title().trim());return ProjectResponse.from(project);}
 record ProjectRequest(@NotBlank @Size(max=160)String title){} record ProjectResponse(UUID id,String title,UUID creatorId,String creatorUsername,Instant createdAt,Instant updatedAt){static ProjectResponse from(ReportingProject p){return new ProjectResponse(p.id,p.title,p.createdBy.getId(),p.createdBy.getUsername(),p.createdAt,p.updatedAt);}}
}

record ErrorBody(String code,String message,String requestId,List<String> details){}
class ApiException extends RuntimeException{final HttpStatus status;final String code;ApiException(HttpStatus s,String c,String m){super(m);status=s;code=c;}}
@RestControllerAdvice class Errors{
 @ExceptionHandler(ApiException.class)ResponseEntity<ErrorBody> api(ApiException e,HttpServletRequest r){return ResponseEntity.status(e.status).body(new ErrorBody(e.code,e.getMessage(),UUID.randomUUID().toString(),List.of()));}
 @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)ResponseEntity<ErrorBody> valid(Exception e,HttpServletRequest r){return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION_ERROR","Request validation failed",UUID.randomUUID().toString(),List.of("title is required")));}
}
class Entry implements AuthenticationEntryPoint{private final JsonMapper j;Entry(JsonMapper x){j=x;}public void commence(HttpServletRequest q,HttpServletResponse r,AuthenticationException e)throws IOException{r.setStatus(401);r.setContentType(MediaType.APPLICATION_JSON_VALUE);j.writeValue(r.getOutputStream(),new ErrorBody("AUTHENTICATION_REQUIRED","Authentication is required",UUID.randomUUID().toString(),List.of()));}}
class Denied implements AccessDeniedHandler{private final JsonMapper j;Denied(JsonMapper x){j=x;}public void handle(HttpServletRequest q,HttpServletResponse r,org.springframework.security.access.AccessDeniedException e)throws IOException{r.setStatus(403);r.setContentType(MediaType.APPLICATION_JSON_VALUE);j.writeValue(r.getOutputStream(),new ErrorBody("FORBIDDEN","You do not have permission for this action",UUID.randomUUID().toString(),List.of()));}}
