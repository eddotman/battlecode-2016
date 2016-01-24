package team032;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

  //Repair allies
  public static void repairAllies(RobotController rc) {
    RobotInfo[] nearbots = rc.senseNearbyRobots(RobotType.ARCHON.sensorRadiusSquared, rc.getTeam());
    double maxHealth = 9999.0;
    if (nearbots.length > 0) {
      RobotInfo healbot = null;
      double healthPercent = 9999.0;

      for (RobotInfo nearbot : nearbots) {

        if (nearbot.health / nearbot.maxHealth < maxHealth) {
          healbot = nearbot;
          maxHealth = nearbot.health / nearbot.maxHealth;
        }
      }

      if (healbot.type != RobotType.ARCHON && rc.getLocation().distanceSquaredTo(healbot.location) < RobotType.ARCHON.attackRadiusSquared) {
        MapLocation repairLoc = healbot.location;

        try {
          rc.repair(repairLoc);
          if (rc.isWeaponReady()) {
            //System.out.println(healbot);
            //rc.repair(repairLoc);
          }
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }

  //Gets nearest robot in a list of robots
  public static int nearestRobInd(RobotInfo[] robots, MapLocation rLoc) {
    int nearestDist = 99999;
    int nearest = 0;
    int dist = 0;
    for (int i = 0; i < robots.length; i++) {
      dist = robots[i].location.distanceSquaredTo(rLoc);
      if (dist < nearestDist) {
        nearest = i;
        nearestDist = dist;
      }
    }
    return nearest;
  }

  //Moves with rotation as needed
  public static void moveToLoc (Direction dir, RobotController rc) {
    if (rc.isCoreReady()) {
      // If possible, move in this direction
      try {
        Direction[] directions = {dir, dir.rotateLeft(), dir.rotateRight(), dir.rotateLeft().rotateLeft(), dir.rotateRight().rotateRight()};

        for (int i = 0; i < directions.length; i++) {
          if (rc.canMove(directions[i]) && rc.onTheMap(rc.getLocation().add(directions[i])) ) {
            rc.move(directions[i]);
            break;
          } else {
            if ( (rc.senseRubble(rc.getLocation().add(directions[i])) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) && rc.onTheMap(rc.getLocation().add(directions[i]) ) ){
              if (rc.isCoreReady()) {
                rc.clearRubble(directions[i]);
                break;
              }
            }
          }
        }
      } catch (Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
      }
    }
  }

  //Scans for neturals to activate
  public static MapLocation findNeutrals(MapLocation rLoc, RobotType rType, RobotController rc) {
    RobotInfo[] neutrals = rc.senseNearbyRobots(rType.sensorRadiusSquared, Team.NEUTRAL);

    if (neutrals.length > 0) {
      //Do signaling
      if (rc.isCoreReady()) {
        if (rType == RobotType.SCOUT) {
          try {
            rc.broadcastMessageSignal(-2, -2, rType.sensorRadiusSquared);
          } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
          }
        }
      }
      for (int i = 0; i < neutrals.length; i++) {
        if (neutrals[i].location.isAdjacentTo(rLoc)) {
          try {
            if (rc.isCoreReady()) {
              rc.activate(neutrals[i].location);
            }
          } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
          }
        }
      }
    }

    return rLoc;
  }

  //Scans for nearby parts
  public static MapLocation findParts(MapLocation rLoc, RobotType rType, RobotController rc) {
    int maxRad = (int)(rType.sensorRadiusSquared / 2.0);
    for (int i = -maxRad; i < maxRad; i++){
      for (int j = -maxRad; j < maxRad; j++){
        MapLocation loc = rLoc.add(i, j);
        if (rc.senseParts(loc) > 0) {
          try {
            //Do signaling
            if (rc.isCoreReady()) {
              if (rType == RobotType.SCOUT) {
                rc.broadcastMessageSignal(-2, -2, rType.sensorRadiusSquared);
              }
            }
          } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
          }
          return loc;
        }
      }
    }
    return rLoc;
  }

  /**
   * run() is the method that is called when a robot is instantiated in the Battlecode world.
   * If this method returns, the robot dies!
   **/
  @SuppressWarnings("unused")
  public static void run(RobotController rc) {
    // You can instantiate variables here.
    Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
        Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    RobotType[] robotTypes = {RobotType.SCOUT, RobotType.SOLDIER, RobotType.SOLDIER, RobotType.SOLDIER,
        RobotType.GUARD, RobotType.GUARD, RobotType.VIPER, RobotType.TURRET};
    Random rand = new Random(rc.getID());
    MapLocation myLoc = null;
    RobotType myType = rc.getType();
    int myAttackRange = 0;
    Team myTeam = rc.getTeam();
    Team enemyTeam = myTeam.opponent();

    if (myType == RobotType.ARCHON) {
      while (true) {
        //Get at beginning of each turn...
        myLoc = rc.getLocation();
        boolean doBuild = true;

        try {
          //Build order
          if (rc.isCoreReady()) {
            repairAllies(rc);

            int typeRoll = rand.nextInt(100);
            RobotType typeToBuild = null;
            if (typeRoll < 5) {
              typeToBuild = RobotType.SCOUT;
            } else if (typeRoll < 45) {
              typeToBuild = RobotType.SOLDIER;
            } else {
              typeToBuild = RobotType.TURRET;
            }

            // Choose a direction to try to move in
            MapLocation partsLoc = findParts(myLoc, RobotType.ARCHON, rc);
            Direction partsDir = myLoc.directionTo(partsLoc);
            MapLocation neutralLoc = findNeutrals(myLoc, RobotType.ARCHON, rc);
            Direction neutralDir = myLoc.directionTo(neutralLoc);
            MapLocation archonLoc = rc.getInitialArchonLocations(myTeam)[0];
            Direction archonDir = myLoc.directionTo(archonLoc);
            Direction moveDir = null;
            RobotInfo[] enemies = rc.senseHostileRobots(myLoc, myType.sensorRadiusSquared);
            int dirToBuild = rand.nextInt(8);



            if (enemies.length != 0 && myLoc.distanceSquaredTo(archonLoc) > 4) {
              //Signal enemy found
              // rc.broadcastMessageSignal(enemies[nearestRobInd(enemies, myLoc)].location.x, enemies[nearestRobInd(enemies, myLoc)].location.y, myType.sensorRadiusSquared);
              moveDir = enemies[nearestRobInd(enemies, myLoc)].location.directionTo(myLoc);
            } else {

              if (archonLoc != myLoc && !myLoc.isAdjacentTo(archonLoc)) {
                moveDir = archonDir;
              } else if (partsLoc != myLoc) {
                moveDir = partsDir;
              } else if (neutralLoc != myLoc) {
                moveDir = neutralDir;
              }
              // else {
              //   // Read signals for parts info...
              //   Signal partSig = rc.readSignal();
              //
              //   if (partSig != null) {
              //     int[] msg = partSig.getMessage();
              //     if (msg != null) { //ARCHON/SCOUT message
              //       if (msg[0] == -2 && msg[1] == -2) { //PARTS or ACTIVATION message
              //         MapLocation moveTo =  partSig.getLocation();
              //         moveDir = myLoc.directionTo(moveTo);
              //       }
              //     } else { //Other message
              //       //moveDir = myLoc.directionTo(partSig.getLocation());
              //     }
              //     rc.emptySignalQueue();
              //   }
              // }
            }

            if (moveDir == null) {
              // Move Randomly
              //moveDir = directions[rand.nextInt(8)];
            }

            if (rc.isCoreReady() && myLoc.distanceSquaredTo(archonLoc) < myType.sensorRadiusSquared / 5.0 && rc.getTeamParts() > 130) {
              if (rc.hasBuildRequirements(typeToBuild)) {
                // Choose a random direction to try to build in
                //Direction[] dirsToBuild = {Direction.NORTH_EAST, Direction.SOUTH_EAST,
                //    Direction.SOUTH_WEST, Direction.NORTH_WEST};

                // If possible, build in this direction
                if (rc.canBuild(directions[dirToBuild], typeToBuild)) {
                  rc.build(directions[dirToBuild], typeToBuild);
                  // if (typeToBuild == RobotType.TURRET) {
                  //   MapLocation buildLoc = myLoc.add(dirToBuild);
                  //   boolean okToBuild = false;
                  //   RobotInfo nBot = rc.senseRobotAtLocation(buildLoc.add(Direction.NORTH));
                  //   RobotInfo eBot = rc.senseRobotAtLocation(buildLoc.add(Direction.EAST));
                  //   RobotInfo wBot = rc.senseRobotAtLocation(buildLoc.add(Direction.WEST));
                  //   RobotInfo sBot = rc.senseRobotAtLocation(buildLoc.add(Direction.SOUTH));
                  //   boolean nTurr = false;
                  //   boolean eTurr = false;
                  //   boolean wTurr = false;
                  //   boolean sTurr = false;
                  //
                  //   if (nBot != null) {nTurr = (nBot.type == RobotType.TURRET);}
                  //   if (eBot != null) {eTurr = (eBot.type == RobotType.TURRET);}
                  //   if (wBot != null) {wTurr = (wBot.type == RobotType.TURRET);}
                  //   if (sBot != null) {sTurr = (sBot.type == RobotType.TURRET);}
                  //
                  //   if (!nTurr && !eTurr && !wTurr && !sTurr) {
                  //     rc.build(dirToBuild, typeToBuild);
                  //   }
                  //
                  // } else {
                  //   rc.build(dirToBuild, typeToBuild);
                  // }
                }
              }
            }

            //Move
            if (moveDir != null) {
              if (rc.senseRubble(myLoc.add(moveDir)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH){
                if (rc.isCoreReady()) {
                  rc.clearRubble(moveDir);
                }
                //Signal disperse
                //rc.broadcastMessageSignal(-1, -1, (int)(myType.sensorRadiusSquared / 4.0));
              } else {
                moveToLoc(moveDir, rc);
              }
            }

          }

          //Signal disperse
          //rc.broadcastMessageSignal(-1, -1, (int)(myType.sensorRadiusSquared / 4.0));

          Clock.yield();
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    } else if (myType == RobotType.SOLDIER || myType == RobotType.SCOUT || myType == RobotType.GUARD) {
      try {
        // Any code here gets executed exactly once at the beginning of the game.
        myAttackRange = myType.attackRadiusSquared;
      } catch (Exception e) {
        // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
        // Caught exceptions will result in a bytecode penalty.
        System.out.println(e.getMessage());
        e.printStackTrace();
      }

      while (true) {
        // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
        // at the end of it, the loop will iterate once per game round.
        try {
          myLoc = rc.getLocation();
          int fate = rand.nextInt(1000);
          boolean shouldAttack = false;
          MapLocation archonLoc = rc.getInitialArchonLocations(myTeam)[0];
          Direction archonDir = myLoc.directionTo(archonLoc);
          Direction moveDir = null;
          boolean disperseMode = false;
          RobotInfo[] enemies = rc.senseHostileRobots(myLoc, myType.sensorRadiusSquared);

          // if (myType == RobotType.SCOUT && rc.isCoreReady() && !disperseMode) {
          //   MapLocation partsLoc = findParts(myLoc, myType, rc);
          // }

          if (enemies.length == 0) {
            //Read signal and move
            Signal moveSig = rc.readSignal();
            if (moveSig != null) {
              int[] msg = moveSig.getMessage();
              if (msg != null) {
                if (msg[0] == -1 && msg[1] == -1) {
                  //Disperse from signal origin
                  MapLocation disperseFrom =  moveSig.getLocation();
                  moveDir = disperseFrom.directionTo(myLoc);
                  disperseMode = true;
                  //System.out.println("DISPERSE!");
                } else { //Move towards signal
                  MapLocation moveTo =  new MapLocation(msg[0], msg[1]);
                  if (myType != RobotType.SCOUT) { moveDir = myLoc.directionTo(moveTo);}
                  //Chain along the message
                  //rc.broadcastSignal(myType.sensorRadiusSquared);
                }
              } else {
                if (myType != RobotType.SCOUT) { moveDir = myLoc.directionTo(moveSig.getLocation()); }
                //Chain along the message
                //rc.broadcastSignal(myType.sensorRadiusSquared);
              }
              rc.emptySignalQueue();
            }
            // else if (myType == RobotType.SCOUT) {
            //   moveDir = archonLoc.directionTo(myLoc);
            // }
            else {
              if (myType == RobotType.SCOUT && myLoc.distanceSquaredTo(archonLoc) > (RobotType.ARCHON.sensorRadiusSquared * 0.2)) {
                moveDir = archonDir;
              } if (myLoc.distanceSquaredTo(archonLoc) > (RobotType.ARCHON.sensorRadiusSquared * 0.4)) {
                moveDir = archonDir;
              }
            }
          }

          if (enemies.length > 0) {
            int thresDist = (int)Math.pow(Math.sqrt(myAttackRange) - 1, 2);
            if (myAttackRange > 0 && myLoc.distanceSquaredTo(enemies[nearestRobInd(enemies, myLoc)].location) < thresDist) {
              moveDir = enemies[nearestRobInd(enemies, myLoc)].location.directionTo(myLoc);
              shouldAttack = true;
            } else if (myAttackRange > 0 && myLoc.distanceSquaredTo(enemies[nearestRobInd(enemies, myLoc)].location) >= thresDist && myLoc.distanceSquaredTo(enemies[nearestRobInd(enemies, myLoc)].location) <= myAttackRange ) {
              moveDir = null;
              shouldAttack = true;
              //Signal enemy found
              //rc.broadcastSignal(myType.sensorRadiusSquared / 2);
            } else if (myAttackRange > 0 && myLoc.distanceSquaredTo(enemies[nearestRobInd(enemies, myLoc)].location) > myAttackRange && myAttackRange >= enemies[nearestRobInd(enemies, myLoc)].type.attackRadiusSquared) {
              moveDir = myLoc.directionTo(enemies[nearestRobInd(enemies, myLoc)].location);
              shouldAttack = false;
              //Signal enemy found
              //rc.broadcastSignal(myType.sensorRadiusSquared / 2);
            } else {
              //moveDir = enemies[nearestRobInd(enemies, myLoc)].location.directionTo(myLoc);
              if (myType == RobotType.SCOUT) {
                //Signal enemy found
                if (enemies[nearestRobInd(enemies, myLoc)].type == RobotType.ZOMBIEDEN) {
                  rc.broadcastMessageSignal(enemies[nearestRobInd(enemies, myLoc)].location.x,enemies[nearestRobInd(enemies, myLoc)].location.y,myType.sensorRadiusSquared*1);
                } else {
                  rc.broadcastMessageSignal(enemies[nearestRobInd(enemies, myLoc)].location.x,enemies[nearestRobInd(enemies, myLoc)].location.y,myType.sensorRadiusSquared*1);
                }
              }
            }
          }

          if (rc.isCoreReady()) {
            if (moveDir != null && moveDir != Direction.OMNI) {
              if (!(rc.senseRubble(myLoc.add(moveDir)) > GameConstants.RUBBLE_OBSTRUCTION_THRESH)){
                moveToLoc(moveDir, rc);
              }
            }
          }

          if (rc.isCoreReady() && shouldAttack) {
            // If this robot type can attack, check for enemies within range and attack one
            if (myAttackRange > 0) {
              RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
              RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);

              if (enemiesWithinRange.length > 0) {
                // Check if weapon is ready
                if (rc.isWeaponReady()) {
                  rc.attackLocation(enemiesWithinRange[nearestRobInd(enemiesWithinRange, myLoc)].location);
                  //Signal enemy found
                  //rc.broadcastSignal(myType.sensorRadiusSquared);
                }
              } else if (zombiesWithinRange.length > 0) {
                // Check if weapon is ready
                if (rc.isWeaponReady()) {
                  rc.attackLocation(zombiesWithinRange[nearestRobInd(zombiesWithinRange, myLoc)].location);
                  //Signal enemy found
                  //rc.broadcastSignal(myType.sensorRadiusSquared);
                }
              }
            }
          }

          if (!shouldAttack) {
            if (rc.isCoreReady()) {
              // Choose a random direction to try to move in
              if (myType == RobotType.SCOUT) {
               moveDir = myLoc.directionTo(archonLoc);
              }
              if (moveDir == null) {
                moveDir = directions[fate % 8];
              }
              if (moveDir != null && moveDir != Direction.OMNI) {
                if (rc.senseRubble(myLoc.add(moveDir)) > GameConstants.RUBBLE_OBSTRUCTION_THRESH && myType != RobotType.SCOUT){
                  if (rc.isCoreReady()) {
                    rc.clearRubble(moveDir);
                  }
                } else {
                  moveToLoc(moveDir, rc);
                }
              }
            }
          }

          Clock.yield();
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    } else if (rc.getType() == RobotType.TURRET) {
      boolean hasMoved = false;
      boolean hasPacked = false;
      try {
        myAttackRange = rc.getType().attackRadiusSquared;
      } catch (Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
      }

      while (true) {
        myLoc = rc.getLocation();
        int fate = rand.nextInt(1000);
        boolean shouldAttack = false;
        MapLocation archonLoc = rc.getInitialArchonLocations(myTeam)[0];
        Direction revArchonDir = archonLoc.directionTo(myLoc);
        //Direction moveDir = null;
        //boolean disperseMode = false;
        // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
        // at the end of it, the loop will iterate once per game round.
        try {

          if (!hasPacked) {
            rc.pack();
            hasPacked = true;
          }

          if (!hasMoved) {
            if (myLoc.distanceSquaredTo(archonLoc) < RobotType.ARCHON.attackRadiusSquared / 6.0) {
              if (rc.isCoreReady()) {
                moveToLoc(revArchonDir, rc);
                rc.unpack();
                hasMoved = true;
              }
            } else {
              rc.unpack();
              hasMoved = true;
            }

          }

          RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
          RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);
          // If this robot type can attack, check for enemies within range and attack one
          if (enemiesWithinRange.length > 0) {
              for (RobotInfo enemy : enemiesWithinRange) {
                  // Check whether the enemy is in a valid attack range (turrets have a minimum range)
                  if (rc.canAttackLocation(enemy.location)) {
                      if (rc.isWeaponReady()) { rc.attackLocation(enemy.location); }
                      break;
                  }
              }
          } else if (zombiesWithinRange.length > 0) {
              for (RobotInfo zombie : zombiesWithinRange) {
                  if (rc.canAttackLocation(zombie.location)) {
                      if (rc.isWeaponReady()) { rc.attackLocation(zombie.location); }
                      break;
                  }
              }
          }

          //Read signal and attack
          Signal atkSig = rc.readSignal();
          if (atkSig != null) {
            int[] msg = atkSig.getMessage();
            if (msg != null) {
              if (msg[0] != -1 && msg[1] != -1) {
                //Not disperse
                MapLocation atkAt =  new MapLocation(msg[0], msg[1]);
                if (rc.canAttackLocation(atkAt)) {
                  if (rc.isWeaponReady()) { rc.attackLocation(atkAt); }
                }
              }
              rc.emptySignalQueue();
            }
          }

          if (fate < 10 && hasMoved && hasPacked && enemiesWithinRange.length == 0 && zombiesWithinRange.length == 0) {
            hasMoved = false;
            hasPacked = false;
          }

          Clock.yield();
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }
}
