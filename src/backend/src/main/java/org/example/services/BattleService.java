package org.example.services;

import org.example.entities.BattleResult;
import org.example.entities.Beyblade;
import org.example.entities.User;
import org.example.game.Arena;
import org.example.game.Battle;
import org.example.game.BattleOutcome;
import org.example.game.Fighter;
import org.example.repositories.BattleResultRepository;
import org.example.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Random;

@Service
public class BattleService {

    private final BattleResultRepository results;
    private final UserRepository users;
    private final BeybladeService beyblades;
    private final Random random = new Random();

    public BattleService(BattleResultRepository results, UserRepository users, BeybladeService beyblades) {
        this.results = results;
        this.users = users;
        this.beyblades = beyblades;
    }

    @Transactional
    public BattleResult fight(User userA, Beyblade beybladeA, User userB, Beyblade beybladeB) {
        if (userA.getId().equals(userB.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Du kannst nicht gegen dich selbst kämpfen.");
        }
        Fighter a = new Fighter(userA.getUsername(), beybladeA.getName(), beybladeA.getStats());
        Fighter b = new Fighter(userB.getUsername(), beybladeB.getName(), beybladeB.getStats());
        BattleOutcome outcome = new Battle(Arena.standard(), a, b, random).run();

        boolean aWon = outcome.winner() == a;
        BattleResult result = new BattleResult();
        result.setWinner(aWon ? userA : userB);
        result.setLoser(aWon ? userB : userA);
        result.setWinnerBeyblade(outcome.winner().beyblade());
        result.setLoserBeyblade(outcome.loser().beyblade());
        result.setFinish(outcome.finish());
        result.setRounds(outcome.rounds());
        return results.save(result);
    }

    @Transactional
    public BattleResult quickMatch(String username, Long beybladeId) {
        User me = users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + username + " nicht gefunden"));
        Beyblade mine = beyblades.owned(me, beybladeId);

        List<User> opponents = users.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> !u.getBeybladeConfigurations().isEmpty())
                .toList();
        if (opponents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gerade ist kein Gegner verfügbar.");
        }
        User opponent = opponents.get(random.nextInt(opponents.size()));
        List<Beyblade> theirs = opponent.getBeybladeConfigurations();
        return fight(me, mine, opponent, theirs.get(random.nextInt(theirs.size())));
    }

    @Transactional(readOnly = true)
    public List<BattleResult> history(String username, int limit) {
        return results.findForUser(username).stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public BattleResult get(Long id) {
        return results.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Kampf " + id + " nicht gefunden"));
    }
}
