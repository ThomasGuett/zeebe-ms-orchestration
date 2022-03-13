import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  //baseUrl = "http://localhost:8090/"
  baseUrl = "https://422d-77-21-209-68.ngrok.io/"
  form = new FormGroup({
    direction: new FormControl('')
  });

  constructor(private http: HttpClient) { }

  ngOnInit(): void {
  }

  async orderCoffee() {
    this.http.post<any>(this.baseUrl + 'startInstance', {
      kafkaTopicName: "coffee",
      kafkaKey: "1",
      direction: this.form.value
    }).subscribe(data => {
      console.log("data", data);
    })
  }
}
